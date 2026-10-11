"""Generate two small synthetic PDFs for manual smoke tests (Pillow required)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).absolute().parents[1] / "benchmark" / "fixtures"
root.mkdir(parents=True, exist_ok=True)
lines = [
    "HÓA ĐƠN BÁN HÀNG",
    "Ngày 11 tháng 10 năm 2026",
    "Khách hàng: Nguyễn Văn An",
    "Sách tiếng Việt: 2 x 85.000 đồng",
    "Tổng cộng: 170.000 đồng",
]
(root / "demo_reference.txt").write_text("\n".join(lines), encoding="utf-8")
image = Image.new("RGB", (1240, 1754), "white")
draw = ImageDraw.Draw(image)
font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 45)
title = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 60)
for index, line in enumerate(lines):
    draw.text((95, 160 + index * 125), line, fill="black", font=title if index == 0 else font)
image.save(root / "demo_scan.pdf", "PDF", resolution=150)

# A minimal PDF with a real text layer to exercise direct extraction.
content = b"BT /F1 22 Tf 50 750 Td (Sample contract) Tj 0 -36 Td (Rent: 500 USD per month) Tj ET"
objects = [
    b"<< /Type /Catalog /Pages 2 0 R >>",
    b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"\nendstream",
]
pdf = bytearray(b"%PDF-1.4\n")
offsets = [0]
for index, obj in enumerate(objects, 1):
    offsets.append(len(pdf))
    pdf += f"{index} 0 obj\n".encode() + obj + b"\nendobj\n"
xref = len(pdf)
pdf += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
for offset in offsets[1:]:
    pdf += f"{offset:010d} 00000 n \n".encode()
pdf += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
(root / "demo_text.pdf").write_bytes(pdf)
