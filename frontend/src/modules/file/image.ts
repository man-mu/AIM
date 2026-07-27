/**
 * 图片处理工具（仅浏览器环境调用，全部带能力检测）。
 */

/** 读取图片尺寸（失败返回 0×0，不阻塞发送）。 */
export async function readImageSize(file: Blob): Promise<{ width: number; height: number }> {
  try {
    if (typeof createImageBitmap === 'function') {
      const bitmap = await createImageBitmap(file);
      const size = { width: bitmap.width, height: bitmap.height };
      bitmap.close();
      return size;
    }
  } catch {
    // fallthrough
  }
  return { width: 0, height: 0 };
}

/**
 * 头像压缩：居中裁方 + 缩放到 256px，输出 JPEG dataURL（~20KB）。
 * dataURL 自包含，mock 模式可直接持久化；真实后端下亦可作上传源。
 */
export async function resizeAvatarToDataUrl(file: Blob, edge = 256): Promise<string> {
  const bitmap = await createImageBitmap(file);
  try {
    const side = Math.min(bitmap.width, bitmap.height);
    const sx = (bitmap.width - side) / 2;
    const sy = (bitmap.height - side) / 2;

    const canvas = document.createElement('canvas');
    canvas.width = edge;
    canvas.height = edge;
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('canvas 2d unavailable');
    }
    context.drawImage(bitmap, sx, sy, side, side, 0, 0, edge, edge);
    return canvas.toDataURL('image/jpeg', 0.86);
  } finally {
    bitmap.close();
  }
}
