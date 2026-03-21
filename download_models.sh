#!/bin/bash

# Stable Diffusion Model Download Script
# Downloads keras-sd TFLite models from HuggingFace

set -e

echo "================================================"
echo "Stable Diffusion Model Downloader"
echo "================================================"
echo ""
echo "This will download ~2GB of model files:"
echo "  - text_encoder.tflite (~127 MB)"
echo "  - diffusion_model.tflite (~1.79 GB)"
echo "  - decoder.tflite (~100 MB)"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Download cancelled."
    exit 0
fi

# Create models directory
MODELS_DIR="models/sd_v14_512"
mkdir -p "$MODELS_DIR"

echo ""
echo "Downloading to: $MODELS_DIR"
echo ""

# Download text encoder
if [ -f "$MODELS_DIR/text_encoder.tflite" ]; then
    echo "✓ text_encoder.tflite already exists"
else
    echo "Downloading text_encoder.tflite..."
    curl -L -o "$MODELS_DIR/text_encoder.tflite" \
        "https://huggingface.co/keras-sd/text-encoder-tflite/resolve/main/text_encoder.tflite"
    echo "✓ text_encoder.tflite downloaded"
fi

# Download diffusion model
if [ -f "$MODELS_DIR/diffusion_model.tflite" ]; then
    echo "✓ diffusion_model.tflite already exists"
else
    echo "Downloading diffusion_model.tflite (this will take a while)..."
    curl -L -o "$MODELS_DIR/diffusion_model.tflite" \
        "https://huggingface.co/keras-sd/diffusion-model-tflite/resolve/main/diffusion_model.tflite"
    echo "✓ diffusion_model.tflite downloaded"
fi

# Download decoder
if [ -f "$MODELS_DIR/decoder.tflite" ]; then
    echo "✓ decoder.tflite already exists"
else
    echo "Downloading decoder.tflite..."
    curl -L -o "$MODELS_DIR/decoder.tflite" \
        "https://huggingface.co/keras-sd/decoder-tflite/resolve/main/decoder.tflite"
    echo "✓ decoder.tflite downloaded"
fi

echo ""
echo "================================================"
echo "Download Complete!"
echo "================================================"
echo ""
echo "Model files saved to: $MODELS_DIR"
echo ""
echo "Total size:"
du -sh "$MODELS_DIR"
echo ""
echo "Next steps:"
echo "1. Copy models to device: adb push models /sdcard/"
echo "2. Or place in app assets (if size permits)"
echo "3. Update app to load from file location"
echo ""
