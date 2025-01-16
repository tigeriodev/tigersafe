/*
 * Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package fr.tigeriodev.tigersafe.data;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.data.PasswordEntry.Data;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;

public final class SafeData implements Destroyable {
    
    private PasswordEntry.Data[] pwEntriesData;
    
    public SafeData(PasswordEntry.Data[] pwEntriesData) {
        this.pwEntriesData = CheckUtils.notNull(pwEntriesData);
    }
    
    public PasswordEntry.Data[] getPwEntriesData() {
        return pwEntriesData;
    }
    
    public void dispose() {
        pwEntriesData = null;
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = true;
        if (pwEntriesData != null) {
            for (Data pwEntData : pwEntriesData) {
                success = MemUtils.tryDestroy(pwEntData) && success;
            }
            pwEntriesData = null;
        }
        
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return pwEntriesData == null;
    }
    
}
