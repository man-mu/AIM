/** 「正在输入」三点动画（reduced-motion 下静态展示）。 */
export function TypingIndicator({ names }: { names: string[] }): React.JSX.Element | null {
  if (names.length === 0) {
    return null;
  }
  const label = names.length === 1 ? `${names[0]} 正在输入` : `${names.length} 人正在输入`;
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-[#0071e3]" role="status" aria-label={label}>
      {label}
      <span aria-hidden className="aim-typing-dots inline-flex gap-0.5">
        <i className="size-1 rounded-full bg-current" />
        <i className="size-1 rounded-full bg-current" />
        <i className="size-1 rounded-full bg-current" />
      </span>
    </span>
  );
}
