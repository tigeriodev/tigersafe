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

import java.time.Instant;
import java.util.Arrays;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public abstract sealed class PasswordEntry implements Comparable<PasswordEntry>, Destroyable
        permits NewPasswordEntry, ExistingPasswordEntry {
    
    public static final class Data implements Destroyable {
        
        public final String name;
        private char[] password;
        public final Instant lastPasswordChangeTime;
        public final String site;
        public final String info;
        public final TOTP totp;
        
        public Data(String name, char[] password, Instant lastPasswordChangeTime, String site,
                String info, TOTP totp) {
            this.name = checkName(name);
            this.password = checkPassword(password);
            this.lastPasswordChangeTime = checkLastPasswordChangeTime(lastPasswordChangeTime);
            this.site = checkSite(site);
            this.info = checkInfo(info);
            this.totp = totp;
        }
        
        /**
         * Should only be used for reading.
         * @return the real value (not a duplicate)
         * @NotNull
         */
        public char[] getPassword() {
            return password;
        }
        
        public static String checkName(String name) {
            return SafeFileManager.checkValidLen(CheckUtils.notEmpty(name));
        }
        
        public static char[] checkPassword(char[] password) {
            return SafeFileManager.checkValidLen(CheckUtils.notEmpty(password));
        }
        
        public static Instant checkLastPasswordChangeTime(Instant lastPasswordChangeTime) {
            if (lastPasswordChangeTime == null || lastPasswordChangeTime.getNano() != 0) {
                throw new IllegalArgumentException(
                        "Invalid lastPasswordChangeTime (nanoseconds are not supported): "
                                + lastPasswordChangeTime + "."
                );
            }
            return lastPasswordChangeTime;
        }
        
        public static String checkSite(String site) {
            return SafeFileManager.checkValidLen(CheckUtils.notNull(site));
        }
        
        public static String checkInfo(String info) {
            return SafeFileManager.checkValidLen(CheckUtils.notNull(info));
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + Arrays.hashCode(password);
            result = prime * result
                    + ((lastPasswordChangeTime == null) ? 0 : lastPasswordChangeTime.hashCode());
            result = prime * result + ((site == null) ? 0 : site.hashCode());
            result = prime * result + ((info == null) ? 0 : info.hashCode());
            result = prime * result + ((totp == null) ? 0 : totp.hashCode());
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Data other = (Data) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (!Arrays.equals(password, other.password))
                return false;
            if (lastPasswordChangeTime == null) {
                if (other.lastPasswordChangeTime != null)
                    return false;
            } else if (!lastPasswordChangeTime.equals(other.lastPasswordChangeTime))
                return false;
            if (site == null) {
                if (other.site != null)
                    return false;
            } else if (!site.equals(other.site))
                return false;
            if (info == null) {
                if (other.info != null)
                    return false;
            } else if (!info.equals(other.info))
                return false;
            if (totp == null) {
                if (other.totp != null)
                    return false;
            } else if (!totp.equals(other.totp))
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            return "Data [name=" + name + ", password="
                    + StringUtils.charArrayToObfuscatedStr(password) + ", lastPasswordChangeTime="
                    + lastPasswordChangeTime + ", site=" + site + ", info=" + info + ", totp="
                    + totp + "]";
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            boolean success = true;
            if (password != null) {
                MemUtils.clearCharArray(password);
                password = null;
            }
            if (totp != null) {
                success = MemUtils.tryDestroy(totp) && success;
            }
            success = MemUtils.tryClearString(name) && success;
            success = MemUtils.tryClearString(site) && success;
            success = MemUtils.tryClearString(info) && success;
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return password == null;
        }
        
    }
    
    /**
     * 
     * @return ephemeral current name, which will potentially be cleared when the name changes and when the password entry is destroyed.
     */
    public abstract String getCurrentName();
    
    public void setName(String newValSrc, SafeDataManager dm) {
        Data.checkName(newValSrc);
        if (newValSrc.equals(getCurrentName())) {
            return;
        }
        setNewName(newValSrc, dm);
    }
    
    protected abstract void setNewName(String newValSrc, SafeDataManager dm);
    
    /**
     * 
     * @return ephemeral current password, which will potentially be cleared when the password changes and when the password entry is destroyed.
     */
    public abstract char[] getCurrentPassword();
    
    public void setPassword(char[] newValSrc) {
        setNewPassword(Data.checkPassword(newValSrc));
    }
    
    protected abstract void setNewPassword(char[] newValSrc);
    
    public abstract Instant getCurrentLastPasswordChangeTime();
    
    /**
     * 
     * @return ephemeral current site, which will potentially be cleared when the site changes and when the password entry is destroyed.
     */
    public abstract String getCurrentSite();
    
    public void setSite(String newValSrc) {
        setNewSite(Data.checkSite(newValSrc));
    }
    
    protected abstract void setNewSite(String newValSrc);
    
    /**
     * 
     * @return ephemeral current info, which will potentially be cleared when the info changes and when the password entry is destroyed.
     */
    public abstract String getCurrentInfo();
    
    public void setInfo(String newValSrc) {
        setNewInfo(Data.checkInfo(newValSrc));
    }
    
    protected abstract void setNewInfo(String newValSrc);
    
    /**
     * 
     * @return ephemeral current TOTO, which will potentially be cleared when the TOTP changes and when the password entry is destroyed.
     */
    public abstract TOTP getCurrentTOTP();
    
    public void setTOTP(TOTP newValSrc) {
        setNewTOTP(newValSrc);
    }
    
    protected abstract void setNewTOTP(TOTP newValSrc);
    
    public abstract Data getData();
    
    public boolean isValid() {
        return getData() != null;
    }
    
    @Override
    public int compareTo(PasswordEntry o) {
        return getCurrentName().compareTo(o.getCurrentName());
    }
    
}
