#!/usr/bin/env python3
"""
Minimal ICO file creator without external dependencies.
Creates a simple icon with Vegas Supermarket colors.
"""
import struct
import os

def create_bmp_data(width, height, bg_color, fg_color):
    """Create 32-bit BGRA BMP data."""
    pixels = []
    for y in range(height):
        row = []
        for x in range(width):
            # Simple gradient/pattern for icon
            if 20 <= x <= width-20 and 20 <= y <= height-20:
                if y < 60:  # Header area - green
                    row.extend([bg_color[2], bg_color[1], bg_color[0], 255])  # BGRA
                elif y > height-40:  # Cart area
                    row.extend([fg_color[2], fg_color[1], fg_color[0], 255])
                else:
                    row.extend([255, 255, 255, 255])  # White middle
            else:
                row.extend([bg_color[2], bg_color[1], bg_color[0], 255])
        pixels.extend(row)
    return bytes(pixels)

def create_ico(filename, size=256):
    """Create a simple ICO file with the given size."""
    bg_color = (42, 95, 26)   # #1a5f2a in BGR
    fg_color = (255, 255, 255)  # White in BGR
    
    # Create BMP data (32-bit BGRA)
    bmp_data = create_bmp_data(size, size, bg_color, fg_color)
    
    # ICO header
    header = struct.pack('<HHH', 0, 1, 1)  # Reserved, Type (1=ICO), Count
    
    # Directory entry
    entry = struct.pack('<BBBBHHII', 
                        size if size < 256 else 0,  # Width
                        size if size < 256 else 0,  # Height
                        0,  # Color palette
                        0,  # Reserved
                        1,  # Color planes
                        32, # Bits per pixel
                        len(bmp_data) + 40,  # Size of image data
                        22  # Offset to image data
                        )
    
    # BMP info header (BITMAPINFOHEADER)
    bmp_header = struct.pack('<IIIHHIIIIII',
        40,           # Header size
        size,         # Width
        size * 2,     # Height (doubled for ICO format)
        1,            # Planes
        32,           # Bits per pixel
        0,            # Compression
        len(bmp_data),# Image size
        0, 0, 0, 0   # DPI and colors
    )
    
    with open(filename, 'wb') as f:
        f.write(header + entry + bmp_header + bmp_data)

# Create icon
output_path = os.path.join(os.path.dirname(__file__), 'src/main/resources/logo.ico')
create_ico(output_path, 256)
print(f"Created: {output_path}")