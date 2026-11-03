import os, shutil, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD = os.path.join(ROOT, "build", "nfc-uid")
OUT = os.path.join(ROOT, "nfc-uid.zip")
STOCK = os.path.join(ROOT, "stock.conf")

if os.path.isdir(BUILD):
    shutil.rmtree(BUILD)

# The module ships the untouched stock config as a safety copy. Every override
# lives in /data/adb/nfc-uid/active.conf, which set.sh creates on first use and
# rewrites in place afterwards, so switching cards never resets the routes.
raw = open(STOCK, "rb").read()
assert raw.count(b"33, 04, 01, 02, 03, 04,") == 1

def w(rel, text, exec_=False):
    p = os.path.join(BUILD, rel)
    d = os.path.dirname(p)
    if d:
        os.makedirs(d, exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    if exec_:
        os.chmod(p, 0o755)

os.makedirs(os.path.join(BUILD, "stock"), exist_ok=True)
open(os.path.join(BUILD, "stock", "libnfc-nxp.conf"), "wb").write(raw)

w("module.prop",
"""id=nfc-uid
name=NFC UID switch (no reboot)
version=v2
versionCode=2
author=local
description=Emulates a MIFARE UID on demand. Switching restarts only the NFC process (about 15 s), no reboot needed. Suica / FeliCa and HCE payments stay untouched.
""")

w("post-fs-data.sh",
"""#!/system/bin/sh
BASE=/data/adb/nfc-uid
[ -f "$BASE/active.conf" ] || exit 0
mount --bind "$BASE/active.conf" /vendor/etc/libnfc-nxp.conf 2>/dev/null
""", True)

w("customize.sh",
"""#!/system/bin/sh
ui_print() { echo "$1"; }
BASE=/data/adb/nfc-uid
mkdir -p "$BASE"
rm -f "$BASE/template.conf"
cp "$MODPATH/stock/libnfc-nxp.conf" "$BASE/stock.conf"
chmod 644 "$BASE/stock.conf"
cat > "$BASE/set.sh" <<'EOS'
#!/system/bin/sh
# usage:
#   set.sh <8 hex>                              switch UID
#   set.sh route <uid> <aid> <isodep> <mifare> <felica> <syscode> <offhost>
#                                               switch UID and all six routes
#   set.sh reset                                drop every override, back to stock
# Mounts have to be created in init's mount namespace, otherwise apps and the
# NFC process (which live in a different namespace) never see them.
if [ "$(readlink /proc/self/ns/mnt)" != "$(readlink /proc/1/ns/mnt)" ]; then
  if command -v nsenter >/dev/null 2>&1; then
    exec nsenter -t 1 -m -- /system/bin/sh "$0" "$@"
  fi
fi
BASE=/data/adb/nfc-uid
CONF=/vendor/etc/libnfc-nxp.conf
ACTIVE=$BASE/active.conf
STOCK=$BASE/stock.conf

hex8() { echo "$1" | tr -d ': -' | tr 'a-f' 'A-F'; }

restart_nfc() {
  kill $(pidof com.android.nfc) 2>/dev/null
  sleep 3
  svc nfc enable
  sleep 2
}

umount_all() {
  for i in 1 2 3 4 5 6; do
    if umount "$CONF" 2>/dev/null; then continue; fi
    if umount -l "$CONF" 2>/dev/null; then continue; fi
    break
  done
}

# First use starts from the stock config with every Type A side route pointed at
# the host so the controller answers the anti-collision itself and therefore uses
# the configured NFCID1. FeliCa routes stay on the eSE.
ensure_active() {
  [ -f "$ACTIVE" ] && return 0
  cp "$STOCK" "$ACTIVE" || return 1
  sed -i -e "s/^DEFAULT_AID_ROUTE=.*/DEFAULT_AID_ROUTE=0x00/" \
         -e "s/^DEFAULT_ISODEP_ROUTE=.*/DEFAULT_ISODEP_ROUTE=0x00/" \
         -e "s/^DEFAULT_MIFARE_CLT_ROUTE=.*/DEFAULT_MIFARE_CLT_ROUTE=0x00/" \
         -e "s/^DEFAULT_OFFHOST_ROUTE=.*/DEFAULT_OFFHOST_ROUTE=0x00/" "$ACTIVE"
  return 0
}

set_uid() {
  B1=$(echo "$1" | cut -c1-2); B2=$(echo "$1" | cut -c3-4)
  B3=$(echo "$1" | cut -c5-6); B4=$(echo "$1" | cut -c7-8)
  sed -i "s/^        33, 04, .*/        33, 04, $B1, $B2, $B3, $B4,/" "$ACTIVE"
  echo "33, 04, $B1, $B2, $B3, $B4,"
}

apply_conf() {
  chmod 644 "$ACTIVE"
  chcon u:object_r:vendor_configs_file:s0 "$ACTIVE" 2>/dev/null
  # Stop NFC before touching the mount so the HAL never re-reads a stale layer.
  svc nfc disable
  sleep 2
  umount_all
  mounted=0
  if command -v nsenter >/dev/null 2>&1; then
    nsenter -t 1 -m -- mount --bind "$ACTIVE" "$CONF" 2>/dev/null && mounted=1
  fi
  if [ $mounted -eq 0 ]; then
    mount --bind "$ACTIVE" "$CONF" 2>/dev/null && mounted=1
  fi
  grep -q "$1" "$CONF" 2>/dev/null && mounted=1
  if [ $mounted -eq 0 ]; then
    echo "bind mount failed"
    return 1
  fi
  restart_nfc
  return 0
}

case "$1" in
  reset)
    rm -f "$ACTIVE"
    umount_all
    restart_nfc
    echo "reset to stock config"
    exit 0
    ;;
  route)
    shift
    U=$(hex8 "$1")
    [ ${#U} -eq 8 ] || { echo "need 8 hex chars"; exit 1; }
    R="$2 $3 $4 $5 $6 $7"
    for v in $R; do
      case "$v" in 0|1|2|3) ;; *) echo "route must be 0..3"; exit 1 ;; esac
    done
    ensure_active || { echo "cannot create active.conf"; exit 1; }
    set -- "$U" $R
    sed -i -e "s/^DEFAULT_AID_ROUTE=.*/DEFAULT_AID_ROUTE=0x0$2/" \
           -e "s/^DEFAULT_ISODEP_ROUTE=.*/DEFAULT_ISODEP_ROUTE=0x0$3/" \
           -e "s/^DEFAULT_MIFARE_CLT_ROUTE=.*/DEFAULT_MIFARE_CLT_ROUTE=0x0$4/" \
           -e "s/^DEFAULT_FELICA_CLT_ROUTE=.*/DEFAULT_FELICA_CLT_ROUTE=0x0$5/" \
           -e "s/^DEFAULT_SYS_CODE_ROUTE=.*/DEFAULT_SYS_CODE_ROUTE=0x0$6/" \
           -e "s/^DEFAULT_OFFHOST_ROUTE=.*/DEFAULT_OFFHOST_ROUTE=0x0$7/" "$ACTIVE"
    PAT=$(set_uid "$1")
    apply_conf "$PAT" || exit 1
    echo "UID=$1 routes=$2$3$4$5$6$7 (verified)"
    exit 0
    ;;
esac

U=$(hex8 "$1")
[ ${#U} -eq 8 ] || { echo "need 8 hex chars"; exit 1; }
ensure_active || { echo "cannot create active.conf"; exit 1; }
PAT=$(set_uid "$U")
apply_conf "$PAT" || exit 1
echo "UID set to $U (verified)"
EOS
chmod 755 "$BASE/set.sh"
ui_print "- installed: /data/adb/nfc-uid/set.sh"
ui_print "- usage: set.sh <uid> | route <uid> <6 routes> | reset"
""", True)

w("action.sh",
"""#!/system/bin/sh
echo "active:"
grep -m1 '33, 04' /vendor/etc/libnfc-nxp.conf
grep -E '^DEFAULT_.*ROUTE=0x' /vendor/etc/libnfc-nxp.conf
""", True)

if os.path.exists(OUT):
    os.remove(OUT)
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for base, dirs, files in os.walk(BUILD):
        for fn in sorted(files):
            full = os.path.join(base, fn)
            z.write(full, os.path.relpath(full, BUILD).replace("\\", "/"))
print("built", OUT, os.path.getsize(OUT))
