#!/bin/sh

set -e

echo "
TigerSafe
Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License version 2 only, as
published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
version 2 for more details (a copy is included in the LICENSE file that
accompanied this code).

You should have received a copy of the GNU General Public License version
2 along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
"

echo "Legal information is provided in the NOTICE and LICENSE ASCII files in the root directory of the TigerSafe project."
echo ""
echo "WARNING: This script will install and modify several things on this computer. You should fully understand what this script and all the things that it will install do before executing them. This script and all the things that it will install are provided absolutely without any warranty, including, but not limited to, any warranty of usefulness, or that they will not break this computer or damage certain aspects of it."
echo ""
echo "WARNING: If you provide custom paths (of files or directories) during this installation process, in order to avoid problems, those paths should contain only latin letters (a to Z) and basic symbols (no accents, no chinese or russian characters...)."

echo ""
read -p "If you understand and accept the terms and conditions of this program, type \"y\" then press Enter to continue, otherwise stop this script by closing the current window: " answer
if [ "$answer" != 'y' ]; then
    echo "You did not confirm that you understand and accept the terms and conditions of this program. The installation of TigerSafe for Unix has been cancelled."
    exit 1
fi
echo ""

rootProjectPath="$(realpath "$(dirname $0)/../..")"
cd "$rootProjectPath"

userDirPath="$HOME/.tigersafe"
if ! [ -d "$userDirPath" ]; then
    mkdir "$userDirPath"
    echo "Created \"$userDirPath\" directory."
fi

defInstallPath="/usr/local/tigersafe"
while true; do
    read -p "Please type a non existing directory where to install TigerSafe, or nothing to use the default one: \"$defInstallPath\" (then press Enter to validate): " installPath
    if [ -z "$installPath" ]; then
        installPath="$defInstallPath"
    fi
    installPath="$(realpath "$installPath")"
    if [ -d "$installPath" ]; then
        echo "The path \"$installPath\" is already used by an existing directory."
        continue
    fi
    
    parentPath="$(dirname "$installPath")"
    if ! [ -d "$parentPath" ]; then
        echo "The directory \"$parentPath\" does not exist."
        continue
    fi
    
    echo "TigerSafe will be installed at \"$installPath\"."
    break
done

mvn clean install -DskipTests
mvn -f ./tigersafe-ui/pom.xml clean -Pjrei

unixSrcPath="./install/unix/src"

unixPath="$installPath/unix"
uninstallPath="$unixPath/uninstall.sh"

jreiPath="$installPath/jrei"
configPath="\$HOME/.tigersafe/config.properties"
launcherSrcPath="./tigersafe-ui/target/tigersafeJREI/bin/tigersafeLauncher"
launcherPath="$jreiPath/bin/tigersafeLauncher"

if ! [ -f "$launcherSrcPath" ]; then
    echo "Failed to build the TigerSafe JREI, TigerSafe cannot be installed."
    exit 1
fi

echo "This script requires superuser (sudo) permission to install TigerSafe files in a protected area."

sudo mkdir "$installPath"
sudo mkdir "$unixPath"
sudo \cp "$unixSrcPath/uninstall.sh" "$uninstallPath"
sudo chmod +x "$uninstallPath"

sed -i'' -e "\$s|\"\$@\"|-config \"$configPath\"|" "$launcherSrcPath"
echo 'read -p "Press Enter to exit: " exitInput' >> "$launcherSrcPath"
sudo \cp "./tigersafe-ui/src/main/resources/icons/app/app-48x48.png" "./tigersafe-ui/target/tigersafeJREI/bin/tigersafeIcon.png"
sudo \mv "./tigersafe-ui/target/tigersafeJREI" "$jreiPath"

sudo chmod -R a-w "$installPath"
sudo chown -R root:root "$installPath"

read -p "Are you in Linux and do you want to create a shortcut for TigerSafe ? Type \"y\" to create a Linux shortcut for TigerSafe, or anything else not to (then press Enter to validate): " answer
if [ "$answer" = "y" ]; then
    shortcutPath="$HOME/.local/share/applications/TigerSafe.desktop"

    if [ -f "$shortcutPath" ]; then
        echo "There is already an (old) shortcut file at: $shortcutPath, it should be removed to be replaced for this new installation."
        rm "$shortcutPath"
        echo "Deleted old \"$shortcutPath\" shortcut file."
    fi

    echo "[Desktop Entry]
    Name=TigerSafe
    Comment=TigerSafe
    Exec=$launcherPath
    Icon=$jreiPath/bin/tigersafeIcon.png
    Terminal=true
    Type=Application" > "$shortcutPath"

    chmod +x "$shortcutPath"
    chmod a-w "$shortcutPath"

    shortcutTxt="
    or by executing the \"TigerSafe\" application."
else
    shortcutTxt=""
fi

echo ""
echo "Successfully installed TigerSafe for Unix at \"$installPath\"."
echo "
TigerSafe can be launched by executing:
  $launcherPath $shortcutTxt

TigerSafe can be uninstalled by executing: $uninstallPath"
