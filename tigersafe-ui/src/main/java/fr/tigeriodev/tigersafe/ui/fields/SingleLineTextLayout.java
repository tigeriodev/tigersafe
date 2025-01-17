/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)
 * 
 * This file contains portions of code from OpenJFX, licensed under the
 * terms of the GNU General Public License version 2 only and copyrighted
 * by Oracle and/or its affiliates, modified by tigeriodev on 16/01/2025.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * DISCLAIMER
 * 
 * This software is not affiliated with, endorsed by, or approved by
 * the following entities: Oracle and/or its affiliates, OpenJFX or any
 * other entity.
 * Any references to these entities are for informational purposes only
 * and do not imply any association, sponsorship, or approval.
 */

package fr.tigeriodev.tigersafe.ui.fields;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.scene.text.TextSpan;
import com.sun.javafx.text.GlyphLayout;
import com.sun.javafx.text.PrismTextLayout;
import com.sun.javafx.text.TextLine;
import com.sun.javafx.text.TextRun;

import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.ReflectionUtils;
import javafx.scene.text.TextAlignment;

/**
 * This class is inspired by {@link PrismTextLayout} from OpenJFX,
 * made to manage single line text layout in a safer way.
 */
public class SingleLineTextLayout extends PrismTextLayout {
    
    private static final Field linesF = ReflectionUtils.getField(PrismTextLayout.class, "lines");
    private static final Field textF = ReflectionUtils.getField(PrismTextLayout.class, "text");
    private static final Field runCountF =
            ReflectionUtils.getField(PrismTextLayout.class, "runCount");
    private static final Field runsF = ReflectionUtils.getField(PrismTextLayout.class, "runs");
    private static final Field flagsF = ReflectionUtils.getField(PrismTextLayout.class, "flags");
    private static final Field layoutWidthF =
            ReflectionUtils.getField(PrismTextLayout.class, "layoutWidth");
    private static final Field layoutHeightF =
            ReflectionUtils.getField(PrismTextLayout.class, "layoutHeight");
    private static final Field logicalBoundsF =
            ReflectionUtils.getField(PrismTextLayout.class, "logicalBounds");
    
    private static final Field runGlyphCountF =
            ReflectionUtils.getField(TextRun.class, "glyphCount");
    private static final Field runGidsF = ReflectionUtils.getField(TextRun.class, "gids");
    private static final Field runPositionsF = ReflectionUtils.getField(TextRun.class, "positions");
    private static final Field runLengthF = ReflectionUtils.getField(TextRun.class, "length");
    private static final Field runWidthF = ReflectionUtils.getField(TextRun.class, "width");
    
    private static final Method shapeMeth = ReflectionUtils.getMeth(
            PrismTextLayout.class,
            "shape",
            TextRun.class,
            char[].class,
            GlyphLayout.class
    );
    private static final Method createLineMeth = ReflectionUtils
            .getMeth(PrismTextLayout.class, "createLine", int.class, int.class, int.class);
    private static final Method computeSideBearingsMeth =
            ReflectionUtils.getMeth(PrismTextLayout.class, "computeSideBearings", TextLine.class);
    
    public SingleLineTextLayout() {
        super();
        super.setAlignment(TextAlignment.LEFT.ordinal());
        super.setLineSpacing(0);
        super.setWrapWidth(0);
        super.setDirection(DIRECTION_LTR);
        super.setTabSize(DEFAULT_TAB_SIZE);
        super.setBoundsType(BOUNDS_CENTER); // TextBoundsType.LOGICAL_VERTICAL_CENTER for Text (default CSS -fx-bounds-type), which means BOUNDS_CENTER here
    }
    
    @Override
    public boolean setContent(String text, Object font) {
        try {
            char[] curText = (char[]) textF.get(this);
            if (curText != null) {
                MemUtils.clearCharArray(curText);
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            ex.printStackTrace();
        }
        befReset();
        
        boolean res = super.setContent(text, font);
        simpleLayout();
        return res;
    }
    
    /**
     * Should be called before {@link PrismTextLayout#reset()}.
     * {@link PrismTextLayout#relayout()} should only be called by {@link PrismTextLayout#reset()}.
     */
    private void befReset() {
        try {
            TextRun[] runs = (TextRun[]) runsF.get(this);
            if (runs != null) {
                if (runs.length != 1) {
                    throw new IllegalStateException("runs length != 1");
                }
                TextRun run = runs[0];
                
                runGlyphCountF.setInt(run, 0);
                
                int[] gids = (int[]) runGidsF.get(run);
                if (gids != null) {
                    MemUtils.clearIntArray(gids);
                    runGidsF.set(run, null);
                }
                
                float[] positions = (float[]) runPositionsF.get(run);
                if (positions != null) {
                    MemUtils.clearFloatArray(positions);
                    runPositionsF.set(run, null);
                }
                
                // charIndices should be null
                
                runLengthF.setInt(run, 0);
                runWidthF.setFloat(run, 0);
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Simple and safe layout process for basic single line text input, inspired by {@link PrismTextLayout#layout()}.
     * Should be called every time {@link PrismTextLayout#lines} is set to null (see {@link PrismTextLayout#ensureLayout()} that is not used and not waited).
     * Before and after this method:
     *  - {@link PrismTextLayout#layoutCache} should be null.
     *  - {@link TextRun#charIndices} should be null (see {@link PrismTextLayout#shape(TextRun, char[], GlyphLayout)}).
     */
    private void simpleLayout() {
        try {
            TextLine[] lines = (TextLine[]) linesF.get(this);
            if (lines != null) {
                throw new IllegalStateException("lines != null"); // lines has probably been set outside of this class, which is unexpected.
            }
            char[] chars = (char[]) textF.get(this);
            
            // buildRuns(chars) start
            
            runCountF.setInt(this, 0);
            
            TextRun[] runs = new TextRun[1];
            runsF.set(this, runs);
            GlyphLayout layout = GlyphLayout.getInstance();
            
            int flags = flagsF.getInt(this);
            flagsF.setInt(
                    this,
                    layout.breakRuns(this, chars, flags | TextLayout.FLAGS_ANALYSIS_VALID)
            ); // FLAGS_ANALYSIS_VALID prevents creation of Bidi object, which would duplicate chars in memory
            layout.dispose();
            
            if (runCountF.getInt(this) != 1) {
                throw new IllegalStateException("runCount != 1");
            }
            
            // buildRuns(chars) end
            
            TextRun run = runs[0];
            shapeMeth.invoke(this, run, chars, null);
            
            TextLine line = (TextLine) createLineMeth.invoke(this, 0, 0, 0);
            lines = new TextLine[1];
            linesF.set(this, lines);
            lines[0] = line;
            
            float layoutWidth = layoutWidthF.getFloat(this);
            float lineY = 0;
            
            RectBounds bounds = line.getBounds();
            
            float lineX = 0;
            line.setAlignment(lineX);
            
            computeSideBearingsMeth.invoke(this, line);
            
            float runX = lineX;
            TextRun[] lineRuns = line.getRuns();
            if (lineRuns.length != 1) {
                throw new IllegalStateException("lineRuns length != 1");
            }
            if (lineRuns[0] != run) {
                throw new IllegalStateException("lineRuns[0] != run");
            }
            run.setLocation(runX, lineY);
            run.setLine(line);
            runX += run.getWidth();
            
            lineY += (bounds.getHeight() - line.getLeading());
            
            float ascent = lines[0].getBounds().getMinY();
            float layoutHeight = lineY;
            layoutHeightF.setFloat(this, layoutHeight);
            
            BaseBounds logicalBounds = (BaseBounds) logicalBoundsF.get(this);
            logicalBoundsF.set(
                    this,
                    logicalBounds.deriveWithNewBounds(
                            0,
                            ascent,
                            0,
                            layoutWidth,
                            layoutHeight + ascent,
                            0
                    )
            );
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Simple and safe hit process for basic single line text input, inspired by {@link PrismTextLayout#getHitInfo(float, float)}.
     */
    @Override
    public Hit getHitInfo(float x, float y) {
        try {
            TextLine[] lines = (TextLine[]) linesF.get(this);
            TextLine line = lines[0];
            TextRun run = line.getRuns()[0];
            RectBounds bounds = line.getBounds();
            x -= bounds.getMinX();
            
            int[] trailing = new int[1];
            int charIndex = run.getStart() + run.getOffsetAtX(x, trailing);
            boolean leading = trailing[0] == 0;
            
            int insertionIndex = charIndex;
            if (!leading) {
                insertionIndex++;
            }
            
            return new Hit(charIndex, insertionIndex, leading);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    @Override
    public boolean setContent(TextSpan[] spans) {
        throw new UnsupportedOperationException();
    }
    
    // Constant properties, for optimization and to prevent sensitive data leak
    
    @Override
    public boolean setAlignment(int alignment) {
        return false;
    }
    
    @Override
    public boolean setWrapWidth(float newWidth) {
        return false;
    }
    
    @Override
    public boolean setLineSpacing(float spacing) {
        return false;
    }
    
    @Override
    public boolean setTabSize(int spaces) {
        return false;
    }
    
    @Override
    public boolean setDirection(int direction) {
        return false;
    }
    
    @Override
    public boolean setBoundsType(int type) {
        return false;
    }
    
}
