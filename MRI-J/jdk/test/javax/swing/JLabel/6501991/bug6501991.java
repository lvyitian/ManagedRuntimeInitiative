/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 *
 * @bug 6501991
 * @author Igor Kushnirskiy
 * @summary tests if ArrayIndexOutOfBoundsException is thrown on painting
 *          JLabel with i18n text in case JLabel has smaller width than needed.
 */

import java.awt.image.*;
import javax.swing.*;

public class bug6501991 {
    public static void main(String[] args) {
        try {
            JLabel l = new JLabel("\u0634\u0634\u0634\u0634\u0634\u0634\u0634");
            l.setSize(5, 22);

            BufferedImage image =
                new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            l.paint(image.createGraphics());
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("failed");
        }
    }
}
