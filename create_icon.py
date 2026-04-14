from PIL import Image, ImageDraw
import os

# Create a 256x256 image
size = 256
img = Image.new('RGB', (size, size), color='#1a5f2a')
draw = ImageDraw.Draw(img)

# Draw a shopping cart icon (simple shape)
# Cart body
draw.polygon([(50, 180), (70, 220), (180, 220), (200, 180)], outline='white', width=4)
# Handle
draw.line([(60, 180), (80, 130), (150, 130), (170, 180)], fill='white', width=4)
# Wheels
draw.ellipse([(80, 225), (100, 245)], fill='white')
draw.ellipse([(150, 225), (170, 245)], fill='white')

# Draw "VEGAS" text using simple shapes
draw.rectangle([60, 80, 196, 110], fill='white')
draw.rectangle([65, 85, 95, 105], fill='#1a5f2a')
draw.rectangle([100, 85, 105, 105], fill='#1a5f2a')
draw.rectangle([110, 85, 135, 105], fill='#1a5f2a')
draw.rectangle([140, 85, 145, 105], fill='#1a5f2a')
draw.rectangle([150, 85, 191, 105], fill='#1a5f2a')

# Save as PNG
output_path = os.path.join(os.path.dirname(__file__), 'src/main/resources/logo.png')
img.save(output_path, 'PNG')
print(f"Created: {output_path}")

# Also create ICO version (256x256 PNG works as ICO for jpackage)
ico_path = os.path.join(os.path.dirname(__file__), 'src/main/resources/logo.ico')
img.save(ico_path, 'ICO')
print(f"Created: {ico_path}")