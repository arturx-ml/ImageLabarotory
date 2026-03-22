#!/usr/bin/env bash
#
# Full pipeline: setup venv → download SD 1.5 checkpoint → convert to MediaPipe bins → ZIP.
#
# Usage:
#   chmod +x convert_and_package.sh
#   ./convert_and_package.sh
#
# Requirements: Python 3.8+, ~10 GB free disk space (4 GB checkpoint + 3.4 GB bins + ZIP)
# Time: ~5-10 min depending on download speed and CPU.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_DIR="${SCRIPT_DIR}/venv"
WORK_DIR="${SCRIPT_DIR}/work"
CKPT_URL="https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.ckpt"
CKPT_FILE="${WORK_DIR}/v1-5-pruned-emaonly.ckpt"
BINS_DIR="${WORK_DIR}/bins"
OUTPUT_ZIP="${SCRIPT_DIR}/mediapipe-sd15-model.zip"

echo "============================================"
echo "  MediaPipe SD 1.5 Model Conversion Pipeline"
echo "============================================"
echo ""

# ── Step 1: Create work directory ──
mkdir -p "${WORK_DIR}"

# ── Step 2: Setup Python virtual environment ──
if [ ! -d "${VENV_DIR}" ]; then
    echo "[1/5] Creating Python virtual environment..."
    python3 -m venv "${VENV_DIR}"
    echo "       Created at ${VENV_DIR}"
else
    echo "[1/5] Virtual environment already exists, reusing."
fi
# shellcheck source=/dev/null
source "${VENV_DIR}/bin/activate"
echo "       Activated venv: $(which python3)"
echo ""

# ── Step 3: Install Python dependencies ──
echo "[2/5] Installing Python dependencies..."
pip install --quiet --upgrade pip
pip install --quiet -r "${SCRIPT_DIR}/requirements.txt"
echo "       Done."
echo ""

# ── Step 4: Download SD 1.5 checkpoint ──
if [ -f "${CKPT_FILE}" ]; then
    echo "[3/5] Checkpoint already downloaded, skipping."
else
    echo "[3/5] Downloading SD 1.5 checkpoint (~4 GB)..."
    echo "       From: ${CKPT_URL}"
    echo "       To:   ${CKPT_FILE}"
    curl -L --progress-bar -o "${CKPT_FILE}" "${CKPT_URL}"
    echo "       Done."
fi
echo ""

# ── Step 5: Convert checkpoint to MediaPipe bins ──
echo "[4/5] Converting checkpoint to MediaPipe format..."
echo "       This may take a few minutes..."
python3 "${SCRIPT_DIR}/convert.py" \
    --ckpt_path "${CKPT_FILE}" \
    --output_path "${BINS_DIR}"
echo ""

# ── Step 6: Package bins into ZIP ──
echo "[5/5] Creating ZIP archive..."
cd "${BINS_DIR}"
zip -r "${OUTPUT_ZIP}" . -x ".*"
cd "${SCRIPT_DIR}"
echo "       Done."
echo ""

# ── Summary ──
BIN_COUNT=$(find "${BINS_DIR}" -name "*.bin" | wc -l | tr -d ' ')
ZIP_SIZE=$(du -sh "${OUTPUT_ZIP}" | cut -f1)

echo "============================================"
echo "  Conversion complete!"
echo "============================================"
echo ""
echo "  Bins directory : ${BINS_DIR}"
echo "  Bin files       : ${BIN_COUNT}"
echo "  ZIP archive     : ${OUTPUT_ZIP}"
echo "  ZIP size        : ${ZIP_SIZE}"
echo ""
echo "  Next steps:"
echo "  1. Upload ${OUTPUT_ZIP} to your server (GCS, S3, or any HTTP host)"
echo "  2. Update MODEL_DOWNLOAD_URL in MediaPipeModelManager.kt"
echo "     with the public URL of the uploaded ZIP"
echo ""
echo "  Optional cleanup (saves ~7 GB):"
echo "  rm -rf ${WORK_DIR}"
echo ""
