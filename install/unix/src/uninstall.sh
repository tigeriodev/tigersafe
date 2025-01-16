#!/bin/sh

set -e

installPath="$(realpath "$(dirname $0)/..")"

while true; do
    installPath="$(realpath "$installPath")"
    if [ -f "$installPath/jrei/bin/tigersafeLauncher" ]; then
        echo "TigerSafe will be uninstalled from \"$installPath\"..."
        break
    fi
    echo "The file \"$installPath/jrei/bin/tigersafeLauncher\" does not exist, the installation root directory of TigerSafe does not seem to be \"$installPath\"."
    read -p "Please type the path to the directory where TigerSafe is currently installed (then press Enter to validate): " installPath
done

read -p "You are about to uninstall TigerSafe from \"$installPath\", please type \"y\" to continue, anything else to cancel (then press Enter to validate): " confirm
if [ "$confirm" != "y" ]; then
    echo "Canceled uninstallation of TigerSafe."
    exit 1
fi

curUserName="$(whoami)"
echo "This script requires superuser (sudo) permission to unlock the installation directory $installPath and delete it..."
sudo chown -R "$curUserName:$curUserName" "$installPath"
sudo chmod -R a+w "$installPath"
sudo \rm -rf "$installPath"

sudo \rm -f "$HOME/.local/share/applications/TigerSafe.desktop"

echo ""
echo "Successfully uninstalled TigerSafe."
