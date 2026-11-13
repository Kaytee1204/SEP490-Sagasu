export const IMAGE_ACCEPT = '.jpg,.jpeg,.jfif,.png,.webp,.heic,.heif,image/jpeg,image/png,image/webp,image/heic,image/heif';
export const IMAGE_FORMATS = 'JPG/JPEG/JFIF, PNG, WebP, HEIC/HEIF';
export const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const MAX_PIXELS = 20_000_000;
const preparedImages = new WeakMap();

async function imageFormat(file) {
  const bytes = new Uint8Array(await file.slice(0, 64).arrayBuffer());
  const text = (start, length) => String.fromCharCode(...bytes.slice(start, start + length));
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'jpeg';
  if ([137, 80, 78, 71, 13, 10, 26, 10].every((value, index) => bytes[index] === value)) return 'png';
  if (text(0, 4) === 'RIFF' && text(8, 4) === 'WEBP') return 'webp';
  if (text(4, 4) === 'ftyp') {
    const brands = [text(8, 4)];
    const boxSize = new DataView(bytes.buffer).getUint32(0);
    for (let offset = 16; offset + 4 <= Math.min(boxSize, bytes.length); offset += 4) brands.push(text(offset, 4));
    if (!brands.some(brand => ['avif', 'avis'].includes(brand)) &&
        brands.some(brand => ['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'hevm', 'hevs', 'mif1', 'msf1'].includes(brand))) return 'heic';
  }
  throw new Error(`Ảnh “${file.name}” không thuộc các định dạng hỗ trợ: ${IMAGE_FORMATS}.`);
}

function imageFile(blob, source, extension) {
  const base = (source.name || 'anh').replace(/\.[^.]+$/, '') || 'anh';
  return new File([blob], `${base}.${extension}`, { type: blob.type, lastModified: source.lastModified ?? Date.now() });
}

async function bitmapFor(file, format) {
  if (format === 'heic') {
    // Load the offline decoder only when an iPhone image is selected.
    const { heicTo } = await import('heic-to/csp');
    return heicTo({ blob: file, type: 'bitmap' });
  }
  return createImageBitmap(file);
}

function hasTransparency(context, width, height) {
  const { data } = context.getImageData(0, 0, width, height);
  for (let offset = 3; offset < data.length; offset += 4) if (data[offset] !== 255) return true;
  return false;
}

const encode = (canvas, mime, quality) => new Promise((resolve, reject) => {
  canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Không tạo được ảnh. Vui lòng chọn lại.')), mime, quality);
});

async function convertImage(file, format, maxBytes) {
  let bitmap;
  const canvas = document.createElement('canvas');
  try {
    bitmap = await bitmapFor(file, format);
    if (!bitmap.width || !bitmap.height) throw new Error('Ảnh không có kích thước hợp lệ.');
    let scale = Math.min(1, Math.sqrt(MAX_PIXELS / (bitmap.width * bitmap.height)), 8192 / Math.max(bitmap.width, bitmap.height));
    let mime;
    for (let attempt = 0; attempt < 7; attempt++) {
      canvas.width = Math.max(1, Math.floor(bitmap.width * scale));
      canvas.height = Math.max(1, Math.floor(bitmap.height * scale));
      const context = canvas.getContext('2d', { willReadFrequently: true });
      if (!context) throw new Error('Trình duyệt không xử lý được ảnh.');
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      mime ||= hasTransparency(context, canvas.width, canvas.height) ? 'image/png' : 'image/jpeg';
      let blob = await encode(canvas, mime, .9);
      if (blob.size > maxBytes && mime === 'image/jpeg') blob = await encode(canvas, mime, .78);
      if (blob.size <= maxBytes) return imageFile(blob, file, mime === 'image/png' ? 'png' : 'jpg');
      scale *= .75;
    }
    throw new Error('Ảnh vẫn vượt giới hạn sau khi xử lý. Vui lòng chọn ảnh nhỏ hơn.');
  } catch (problem) {
    throw new Error(`Không xử lý được ảnh “${file.name}”. ${problem instanceof Error ? problem.message : 'Vui lòng chọn ảnh khác hoặc xuất ảnh sang JPG/PNG.'}`);
  } finally {
    bitmap?.close();
    canvas.width = canvas.height = 0;
  }
}

export async function prepareImageFiles(files, { maxBytes = IMAGE_MAX_BYTES, allowPdf = false, pdfMaxBytes = 10 * 1024 * 1024 } = {}) {
  const prepared = [];
  // Process sequentially so selecting five large photos does not decode all five at once.
  for (const file of files) {
    const pdf = allowPdf && String.fromCharCode(...new Uint8Array(await file.slice(0, 5).arrayBuffer())) === '%PDF-';
    const limit = pdf ? pdfMaxBytes : maxBytes;
    if (!file.size || file.size > limit) throw new Error(`Tệp “${file.name}” phải có dung lượng lớn hơn 0 và không quá ${limit / 1048576} MB.`);
    if (pdf) { prepared.push(file); continue; }
    let pending = preparedImages.get(file);
    if (!pending) {
      pending = (async () => {
        const format = await imageFormat(file);
        if (format === 'jpeg' || format === 'png') {
          const mime = format === 'jpeg' ? 'image/jpeg' : 'image/png';
          return imageFile(new Blob([file], { type: mime }), file, format === 'jpeg' ? 'jpg' : 'png');
        }
        return convertImage(file, format, maxBytes);
      })();
      preparedImages.set(file, pending);
    }
    try {
      const image = await pending;
      if (image.size > maxBytes) throw new Error(`Ảnh “${file.name || 'đính kèm'}” vượt giới hạn ${maxBytes / 1048576} MB.`);
      preparedImages.set(image, Promise.resolve(image));
      prepared.push(image);
    } catch (problem) {
      preparedImages.delete(file);
      throw problem;
    }
  }
  return prepared;
}

export async function prepareImageFormData(form) {
  const prepared = new FormData();
  for (const [name, value] of form.entries()) {
    const image = value instanceof Blob && (name === 'images' || value.type.startsWith('image/') ||
      /\.(jpe?g|jfif|png|webp|heic|heif)$/i.test(value.name || ''));
    if (image) {
      try {
        // Cover restored drafts and selections retained across a frontend update, too.
        const [file] = await prepareImageFiles([value], { maxBytes: Math.max(IMAGE_MAX_BYTES, value.size) });
        prepared.append(name, file, file.name);
      } catch (problem) {
        problem.status = 400;
        problem.code = 'IMAGE_PREPARATION_FAILED';
        throw problem;
      }
    } else if (value instanceof Blob) prepared.append(name, value, value.name || 'blob');
    else prepared.append(name, value);
  }
  return prepared;
}
