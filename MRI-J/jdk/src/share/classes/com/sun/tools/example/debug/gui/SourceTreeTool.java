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

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;

public class SourceTreeTool extends JPanel {

    private Environment env;

    private ExecutionManager runtime;
    private SourceManager sourceManager;
    private ClassManager classManager;

    private JTree tree;
    private SourceTreeNode root;
    private SearchPath sourcePath;
    private CommandInterpreter interpreter;

    private static String HEADING = "SOURCES";

    public SourceTreeTool(Environment env) {

        super(new BorderLayout());

        this.env = env;
        this.runtime = env.getExecutionManager();
        this.sourceManager = env.getSourceManager();

        this.interpreter = new CommandInterpreter(env);

        sourcePath = sourceManager.getSourcePath();
        root = createDirectoryTree(HEADING);

        // Create a tree that allows one selection at a time.
        tree = new JTree(new DefaultTreeModel(root));
        tree.setSelectionModel(new SingleLeafTreeSelectionModel());

        /******
        // Listen for when the selection changes.
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                SourceTreeNode node = (SourceTreeNode)
                    (e.getPath().getLastPathComponent());
                interpreter.executeCommand("view " + node.getRelativePath());
            }
        });
        ******/

        MouseListener ml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                if(selRow != -1) {
                    if(e.getClickCount() == 1) {
                        SourceTreeNode node =
                            (SourceTreeNode)selPath.getLastPathComponent();
                        // If user clicks on leaf, select it, and issue 'view' command.
                        if (node.isLeaf()) {
                            tree.setSelectionPath(selPath);
                            interpreter.executeCommand("view " + node.getRelativePath());
                        }
                    }
                }
            }
        };
        tree.addMouseListener(ml);

        JScrollPane treeView = new JScrollPane(tree);
        add(treeView);

        // Create listener for source path changes.

        SourceTreeToolListener listener = new SourceTreeToolListener();
        sourceManager.addSourceListener(listener);

        //### remove listeners on exit!
    }

    private class SourceTreeToolListener implements SourceListener {

        public void sourcepathChanged(SourcepathChangedEvent e) {
            sourcePath = sourceManager.getSourcePath();
            root = createDirectoryTree(HEADING);
            tree.setModel(new DefaultTreeModel(root));
        }

    }

    private static class SourceOrDirectoryFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return (name.endsWith(".java") ||
                    new File(dir, name).isDirectory());
        }
    }

    private static FilenameFilter filter = new SourceOrDirectoryFilter();

    SourceTreeNode createDirectoryTree(String label) {
        try {
            return new SourceTreeNode(label, null, "", true);
        } catch (SecurityException e) {
            env.failure("Cannot access source file or directory");
            return null;
        }
    }


    class SourceTreeNode implements TreeNode {

        private String name;
        private boolean isDirectory;
        private SourceTreeNode parent;
        private SourceTreeNode[] children;
        private String relativePath;
        private boolean isExpanded;

        private SourceTreeNode(String label,
                               SourceTreeNode parent,
                               String relativePath,
                               boolean isDirectory) {
            this.name = label;
            this.relativePath = relativePath;
            this.parent = parent;
            this.isDirectory = isDirectory;
        }

        public String toString() {
            return name;
        }

        public String getRelativePath() {
            return relativePath;
        }

        private void expandIfNeeded() {
            try {
                if (!isExpanded && isDirectory) {
                    String[] files = sourcePath.children(relativePath, filter);
                    children = new SourceTreeNode[files.length];
                    for (int i = 0; i < files.length; i++) {
                        String childName =
                            (relativePath.equals(""))
                            ? files[i]
                            : relativePath + File.separator + files[i];
                        File file = sourcePath.resolve(childName);
                        boolean isDir = (file != null && file.isDirectory());
                        children[i] =
                            new SourceTreeNode(files[i], this, childName, isDir);
                    }
                }
                isExpanded = true;
            } catch (SecurityException e) {
                children = null;
                env.failure("Cannot access source file or directory");
            }
        }

        // -- interface TreeNode --

        /*
         * Returns the child <code>TreeNode</code> at index
         * <code>childIndex</code>.
         */
        public TreeNode getChildAt(int childIndex) {
            expandIfNeeded();
            return children[childIndex];
        }

        /**
         * Returns the number of children <code>TreeNode</code>s the receiver
         * contains.
         */
        public int getChildCount() {
            expandIfNeeded();
            return children.length;
        }

        /**
         * Returns the parent <code>TreeNode</code> of the receiver.
         */
        public TreeNode getParent() {
            return parent;
        }

        /**
         * Returns the index of <code>node</code> in the receivers children.
         * If the receiver does not contain <code>node</code>, -1 will be
         * returned.
         */
        public int getIndex(TreeNode node) {
            expandIfNeeded();
            for (int i = 0; i < children.length; i++) {
                if (children[i] == node)
                    return i;
            }
            return -1;
        }

        /**
         * Returns true if the receiver allows children.
         */
        public boolean getAllowsChildren() {
            return isDirectory;
        }

        /**
         * Returns true if the receiver is a leaf.
         */
        public boolean isLeaf() {
            expandIfNeeded();
            return !isDirectory;
        }

        /**
         * Returns the children of the receiver as an Enumeration.
         */
        public Enumeration children() {
            expandIfNeeded();
            return new Enumeration() {
                int i = 0;
                public boolean hasMoreElements() {
                    return (i < children.length);
                }
                public Object nextElement() throws NoSuchElementException {
                    if (i >= children.length) {
                        throw new NoSuchElementException();
                    }
                    return children[i++];
                }
            };
        }

    }

}
