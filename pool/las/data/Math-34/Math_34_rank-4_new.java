/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.graphics.shading;

import java.awt.PaintContext;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.util.Matrix;

/**
 * Intermediate class extended by the shading types 4,5,6 and 7 that contains
 * the common methods used by these classes.
 *
 * @author Shaola Ren
 * @author Tilman Hausherr
 */
abstract class TriangleBasedShadingContext extends ShadingContext implements PaintContext
{
    private static final Log LOG = LogFactory.getLog(TriangleBasedShadingContext.class);

    /**
     * bits per coordinate.
     */
    protected int bitsPerCoordinate;

    /**
     * bits per color component
     */
    protected int bitsPerColorComponent;

    /**
     * number of color components.
     */
    protected int numberOfColorComponents;

    /**
     * background values.
     */
    protected float[] background;
    protected int rgbBackground;

    final protected boolean hasFunction;

    protected Map<Point, Integer> pixelTable;

    public TriangleBasedShadingContext(PDShading shading, ColorModel cm,
            AffineTransform xform, Matrix ctm, Rectangle dBounds)
            throws IOException
    {
        super(shading, cm, xform, ctm, dBounds);
        PDTriangleBasedShadingType triangleBasedShadingType = (PDTriangleBasedShadingType) shading;
        hasFunction = shading.getFunction() != null;
        bitsPerCoordinate = triangleBasedShadingType.getBitsPerCoordinate();
        LOG.debug("bitsPerCoordinate: " + (Math.pow(2, bitsPerCoordinate) - 1));
        bitsPerColorComponent = triangleBasedShadingType.getBitsPerComponent();
        LOG.debug("bitsPerColorComponent: " + bitsPerColorComponent);
        numberOfColorComponents = hasFunction ? 1 : shadingColorSpace.getNumberOfComponents();
        LOG.debug("numberOfColorComponents: " + numberOfColorComponents);
    }

    // get the points from the triangles, calculate their color and add 
    // point-color mappings to the map
    protected void calcPixelTable(List<ShadedTriangle> triangleList, Map<Point, Integer> map)
    {
        for (ShadedTriangle tri : triangleList)
        {
            int degree = tri.getDeg();
            if (degree == 2)
            {
                Line line = tri.getLine();
                for (Point p : line.linePoints)
                {
                    map.put(p, convertToRGB(line.calcColor(p)));
                }
            }
            else
            {
                int[] boundary = tri.getBoundary();
                boundary[0] = Math.max(boundary[0], deviceBounds.x);
                boundary[1] = Math.min(boundary[1], deviceBounds.x + deviceBounds.width);
                boundary[2] = Math.max(boundary[2], deviceBounds.y);
                boundary[3] = Math.min(boundary[3], deviceBounds.y + deviceBounds.height);
                for (int x = boundary[0]; x <= boundary[1]; x++)
                {
                    for (int y = boundary[2]; y <= boundary[3]; y++)
                    {
                        Point p = new Point(x, y);
                        if (tri.contains(p))
                        {
                            map.put(p, convertToRGB(tri.calcColor(p)));
                        }
                    }
                }
            }
        }
    }

    // transform a point from source space to device space
    protected void transformPoint(Point2D p, Matrix ctm, AffineTransform xform)
    {
        if (ctm != null)
        {
            ctm.createAffineTransform().transform(p, p);
        }
        xform.transform(p, p);
    }

    // convert color to RGB color value, using function if required,
    // then convert from the shading colorspace to an RGB value,
    // which is encoded into an integer.
    @Override
    protected int convertToRGB(float[] values)
    {
        if (hasFunction)
        {
            try
            {
                values = shading.evalFunction(values);
            }
            catch (IOException exception)
            {
                LOG.error("error while processing a function", exception);
            }
        }
        return super.convertToRGB(values);
    }

    // true if the relevant list is empty
    abstract boolean emptyList();
    
    @Override
    public final ColorModel getColorModel()
    {
        return outputColorModel;
    }

    @Override
    public void dispose()
    {
        outputColorModel = null;
        shadingColorSpace = null;
    }

    @Override
    public final Raster getRaster(int x, int y, int w, int h)
    {
        WritableRaster raster = getColorModel().createCompatibleWritableRaster(w, h);
        int[] data = new int[w * h * 4];
        if (!emptyList() || background != null)
        {
            for (int row = 0; row < h; row++)
            {
                int currentY = y + row;
                if (bboxRect != null)
                {
                    if (currentY < minBBoxY || currentY > maxBBoxY)
                    {
                        continue;
                    }
                }
                for (int col = 0; col < w; col++)
                {
                    int currentX = x + col;
                    if (bboxRect != null)
                    {
                        if (currentX < minBBoxX || currentX > maxBBoxX)
                        {
                            continue;
                        }
                    }
                    Point p = new Point(currentX, currentY);
                    int value;
                    if (pixelTable.containsKey(p))
                    {
                        value = pixelTable.get(p);
                    }
                    else
                    {
                        if (background != null)
                        {
                            value = rgbBackground;
                        }
                        else
                        {
                            continue;
                        }
                    }
                    int index = (row * w + col) * 4;
                    data[index] = value & 255;
                    value >>= 8;
                    data[index + 1] = value & 255;
                    value >>= 8;
                    data[index + 2] = value & 255;
                    data[index + 3] = 255;
                }
            }
        }
        raster.setPixels(0, 0, w, h, data);
        return raster;
    }

}
