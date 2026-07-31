/** iOS 风格开关（原生 checkbox 语义，键盘/读屏友好）。 */
export interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  disabled?: boolean;
}

export function Switch({ checked, onChange, label, disabled = false }: SwitchProps): React.JSX.Element {
  return (
    <label className={`inline-flex items-center ${disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}>
      <input
        type="checkbox"
        role="switch"
        aria-label={label}
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        className="peer sr-only"
      />
      <span
        aria-hidden
        className="relative h-[22px] w-9 rounded-full bg-[#d2d2d7] transition-colors duration-200 peer-checked:bg-[#30c552] peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-[#0071e3] after:absolute after:top-[2px] after:left-[2px] after:size-[18px] after:rounded-full after:bg-white after:shadow-[0_1px_3px_rgba(0,0,0,0.25)] after:transition-transform after:duration-200 peer-checked:after:translate-x-[14px]"
      />
    </label>
  );
}
