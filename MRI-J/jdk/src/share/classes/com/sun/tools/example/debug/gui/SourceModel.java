/*
 * Copyright 1998-1999 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.example.debug.gui;

import java.io.*;
import java.util.*;

import com.sun.jdi.*;
import com.sun.jdi.request.*;

import com.sun.tools.example.debug.bdi.*;

import javax.swing.*;

/**
 * Represents and manages one source file.
 * Caches source lines.  Holds other source file info.
 */
public class SourceModel extends AbstractListModel {

    private File path;

    boolean isActuallySource = true;

    private List<ReferenceType> classes = new ArrayList<ReferenceType>();

    private Environment env;

    // Cached line-by-line access.

    //### Unify this with source model used in source view?
    //### What is our cache-management policy for these?
    //### Even with weak refs, we won't discard any part of the
    //### source if the SourceModel object is reachable.
    /**
     * List of Line.
     */
    private List<Line> sourceLines = null;

    public static class Line {
        public String text;
        public boolean hasBreakpoint = false;
        public ReferenceType refType = null;
        Line(String text) {
            this.text = text;
        }
        public boolean isExecutable() {
            return refType != null;
        }
        public boolean hasBreakpoint() {
            return hasBreakpoint;
        }
    };

    // 132 characters long, all printable characters.
    public static final Line prototypeCellValue = new Line(
                                        "abcdefghijklmnopqrstuvwxyz" +
                                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                                        "1234567890~!@#$%^&*()_+{}|" +
                                        ":<>?`-=[];',.XXXXXXXXXXXX/\\\"");

    SourceModel(Environment env, File path) {
        this.env = env;
        this.path = path;
    }

    public SourceModel(String message) {
        this.path = null;
        setMessage(message);
    }

    private void setMessage(String message) {
        isActuallySource = false;
        sourceLines = new ArrayList<Line>();
        sourceLines.add(new Line(message));
    }

    // **** Implement ListModel  *****

    public Object getElementAt(int index) {
        if (sourceLines == null) {
            initialize();
        }
        return sourceLines.get(index);
    }

    public int getSize() {
        if (sourceLines == null) {
            initialize();
        }
        return sourceLines.size();
    }

    // ***** Other functionality *****

    public File fileName() {
        return path;
    }

    public BufferedReader sourceReader() throws IOException {
        return new BufferedReader(new FileReader(path));
    }

    public Line line(int lineNo) {
        if (sourceLines == null) {
            initialize();
        }
        int index = lineNo - 1; // list is 0-indexed
        if (index >= sourceLines.size() || index < 0) {
            return null;
        } else {
            return sourceLines.get(index);
        }
    }

    public String sourceLine(int lineNo) {
        Line line = line(lineNo);
        if (line == null) {
            return null;
        } else {
            return line.text;
        }
    }

    void addClass(ReferenceType refType) {
        // Logically is Set
        if (classes.indexOf(refType) == -1) {
            classes.add(refType);
            if (sourceLines != null) {
                markClassLines(refType);
            }
        }
    }

    /**
     * @return List of currently known {@link com.sun.jdi.ReferenceType}
     * in this source file.
     */
    public List<ReferenceType> referenceTypes() {
        return Collections.unmodifiableList(classes);
    }

    private void initialize() {
        try {
            rawInit();
        } catch (IOException exc) {
            setMessage("[Error reading source code]");
        }
    }

    public void showBreakpoint(int ln, boolean hasBreakpoint) {
        line(ln).hasBreakpoint = hasBreakpoint;
        fireContentsChanged(this, ln, ln);
    }

    public void showExecutable(int ln, ReferenceType refType) {
        line(ln).refType = refType;
        fireContentsChanged(this, ln, ln);
    }

    /**
     * Mark executable lines and breakpoints, but only
     * when sourceLines is set.
     */
    private void markClassLines(ReferenceType refType) {
        List methods = refType.methods();
        for (Iterator mit = methods.iterator(); mit.hasNext();) {
            Method meth = (Method)mit.next();
            try {
                List lines = meth.allLineLocations();
                for (Iterator lit = lines.iterator(); lit.hasNext();) {
                    Location loc = (Location)lit.next();
                    showExecutable(loc.lineNumber(), refType);
                }
            } catch (AbsentInformationException exc) {
                // do nothing
            }
        }
        List bps = env.getExecutionManager().eventRequestManager().breakpointRequests();
        for (Iterator it = bps.iterator(); it.hasNext();) {
            BreakpointRequest bp = (BreakpointRequest)it.next();
            if (bp.location() != null) {
                Location loc = bp.location();
                if (loc.declaringType().equals(refType)) {
                    showBreakpoint(loc.lineNumber(),true);
                }
            }
        }
    }

    private void rawInit() throws IOException {
        sourceLines = new ArrayList<Line>();
        BufferedReader reader = sourceReader();
        try {
            String line = reader.readLine();
            while (line != null) {
                sourceLines.add(new Line(expandTabs(line)));
                line = reader.readLine();
            }
        } finally {
            reader.close();
        }
        for (Iterator it = classes.iterator(); it.hasNext();) {
            markClassLines((ClassType)it.next());
        }
    }

    private String expandTabs(String s) {
        int col = 0;
        int len = s.length();
        StringBuffer sb = new StringBuffer(132);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            sb.append(c);
            if (c == '\t') {
                int pad = (8 - (col % 8));
                for (int j = 0; j < pad; j++) {
                    sb.append(' ');
                }
                col += pad;
            } else {
                col++;
            }
        }
        return sb.toString();
    }

}
