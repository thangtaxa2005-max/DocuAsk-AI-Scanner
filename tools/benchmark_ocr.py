"""Measure character/word error rate for paired Vietnamese OCR transcripts.

Usage: python tools/benchmark_ocr.py benchmark/pairs.csv
CSV columns: document,engine,reference,hypothesis (paths relative to CSV).
Keep independent human transcripts as references; do not score generated copies.
"""
import csv
import sys
import unicodedata
from pathlib import Path


def distance(a, b):
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        current = [i]
        for j, y in enumerate(b, 1):
            current.append(min(current[-1] + 1, prev[j] + 1, prev[j - 1] + (x != y)))
        prev = current
    return prev[-1]


def normalize(text):
    return " ".join(unicodedata.normalize("NFC", text).split())


def main(csv_path):
    root = csv_path.parent
    totals = {}
    with csv_path.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            truth = normalize((root / row["reference"]).read_text(encoding="utf-8"))
            result = normalize((root / row["hypothesis"]).read_text(encoding="utf-8"))
            if not truth:
                raise ValueError(f"Empty reference: {row['reference']}")
            char_errors = distance(truth, result)
            words_truth, words_result = truth.split(), result.split()
            word_errors = distance(words_truth, words_result)
            entry = totals.setdefault(row["engine"], [0, 0, 0, 0, 0])
            entry[0] += 1
            entry[1] += char_errors
            entry[2] += len(truth)
            entry[3] += word_errors
            entry[4] += len(words_truth)
            print(f"{row['document']} / {row['engine']}: CER={char_errors/len(truth):.3f} WER={word_errors/len(words_truth):.3f}")
    print("\nEngine,Documents,CER,WER")
    for engine, (count, chars, n_chars, words, n_words) in sorted(totals.items()):
        print(f"{engine},{count},{chars/n_chars:.3f},{words/n_words:.3f}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python tools/benchmark_ocr.py benchmark/pairs.csv")
    main(Path(sys.argv[1]))
