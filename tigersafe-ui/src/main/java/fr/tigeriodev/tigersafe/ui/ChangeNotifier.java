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

package fr.tigeriodev.tigersafe.ui;

import java.util.Arrays;

public class ChangeNotifier {
    
    private Runnable[] listeners = null;
    
    /**
     * NB: Should never add several times the same listener.
     * @param listener
     */
    public void addListener(Runnable listener) {
        listeners = listeners != null
                ? Arrays.copyOf(listeners, listeners.length + 1)
                : new Runnable[1];
        listeners[listeners.length - 1] = listener;
    }
    
    public void notifyListeners() {
        if (listeners == null) {
            return;
        }
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
    
    /**
     * NB: Should not call this method with a listener that is not/no longer listening.
     * @param listener
     */
    public void remListener(Runnable listener) {
        if (listeners == null) {
            throw new IllegalStateException("listeners is null.");
        }
        Runnable[] newListeners = new Runnable[listeners.length - 1];
        boolean found = false;
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] != listener) {
                newListeners[i] = listeners[i];
            } else {
                if (found) {
                    throw new IllegalStateException(
                            listener + " listener has been added several times."
                    );
                }
                found = true;
            }
        }
        if (found) {
            listeners = newListeners;
        } else {
            throw new IllegalStateException(listener + " listener has not been added.");
        }
    }
    
    public void remAllListeners() {
        listeners = null;
    }
    
}
