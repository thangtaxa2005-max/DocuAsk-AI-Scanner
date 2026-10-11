# Đo OCR tiếng Việt

Chuẩn bị 10–15 trang thật, gồm hóa đơn, hợp đồng và sách. Mỗi trang cần một bản chép tay độc lập (`reference`) và đầu ra từ từng engine (`hypothesis`). Tạo `pairs.csv` với cột `document,engine,reference,hypothesis`; đường dẫn tính từ thư mục chứa CSV. Chạy `python tools/benchmark_ocr.py benchmark/pairs.csv` để tính CER và WER. Cùng một bộ trang, ảnh và quy tắc chép chuẩn phải được dùng cho mọi engine.

Chưa có bộ ảnh/bản chép chuẩn trong workspace, vì vậy dự án chưa công bố số liệu hay khẳng định engine thắng. ML Kit Latin được dùng làm baseline vì chạy trên máy, không cần mạng hoặc key. Có thể bổ sung kết quả Tesseract và Gemini Vision vào cùng CSV trước khi chốt kết luận.

Để đo trả lời đúng, chuẩn bị ít nhất 15 câu hỏi có đáp án và trang nguồn do người đánh giá xác nhận. Ghi `đúng/sai`, `trích dẫn đúng/sai`, và trường hợp AI từ chối khi tài liệu không có đáp án. Không tính câu hỏi do AI tự tạo từ chính tài liệu vào bộ kiểm thử.
