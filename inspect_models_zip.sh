#!/usr/bin/env bash
# Inspects anthrapper/stable_diffusion_android models.zip
# Downloads the file and lists contents without fully extracting

set -e

URL="https://huggingface.co/anthrapper/stable_diffusion_android/resolve/main/models.zip"
DEST="$HOME/Downloads/anthrapper_models.zip"

echo "=== anthrapper/stable_diffusion_android — models.zip inspector ==="
echo ""

# Download if not already present
if [ -f "$DEST" ]; then
    echo "Already downloaded: $DEST"
else
    echo "Downloading models.zip (786 MB) to $DEST ..."
    echo "This will take a few minutes depending on your connection."
    echo ""
    curl -L --progress-bar -o "$DEST" "$URL"
    echo ""
    echo "Download complete."
fi

echo ""
echo "=== Contents of models.zip ==="
unzip -l "$DEST" | awk '
    NR==1 { print; next }          # header line
    /-----/ { print; next }        # separator lines
    {
        # print size, date, time, filename
        size=$1; file=$NF
        if (size+0 > 0) {
            mb = size / 1048576
            printf "  %8.1f MB   %s\n", mb, file
        } else {
            print
        }
    }
'

echo ""
echo "=== File type summary ==="
unzip -l "$DEST" | grep -oE '\.[a-zA-Z0-9]+$' | sort | uniq -c | sort -rn

echo ""
echo "=== Key findings ==="
unzip -l "$DEST" | grep -iE '\.(tflite|ort|onnx|bin|pt|pb)' | \
    awk '{ size=$1; file=$NF; mb=size/1048576; printf "  %8.1f MB   %s\n", mb, file }' | \
    sort -t'M' -k1 -rn

echo ""
echo "Done. Inspect the output above to check if a quantized diffusion/UNet model is present."
