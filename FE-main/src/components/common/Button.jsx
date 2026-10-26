// src/components/common/Button.jsx
import React from 'react';
import { Loader2 } from 'lucide-react';

const VARIANT_STYLES = {
  primary: 'bg-[var(--teal-600)] hover:bg-[var(--teal-700)] text-white shadow-sm hover:shadow transition-all',
  secondary: 'bg-zinc-100 hover:bg-zinc-200 text-zinc-800 transition-colors',
  outline: 'border border-[var(--border-strong)] bg-white hover:bg-[var(--teal-50)] text-zinc-700 transition-colors',
  ghost: 'bg-transparent hover:bg-zinc-100 text-zinc-700 transition-colors',
  danger: 'bg-red-600 hover:bg-red-700 text-white shadow-sm transition-colors',
  success: 'bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm transition-colors',
};

const SIZE_STYLES = {
  sm: 'px-3 py-1.5 text-xs rounded-lg gap-1.5 font-medium',
  md: 'px-4 py-2 text-sm rounded-xl gap-2 font-medium',
  lg: 'px-5 py-2.5 text-base rounded-xl gap-2.5 font-semibold',
};

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  disabled = false,
  icon: Icon = null,
  iconPosition = 'left',
  fullWidth = false,
  className = '',
  type = 'button',
  ...props
}) {
  const baseClasses = 'inline-flex items-center justify-center cursor-pointer select-none transition-all disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-[var(--teal-500)]/40';
  const variantClass = VARIANT_STYLES[variant] || VARIANT_STYLES.primary;
  const sizeClass = SIZE_STYLES[size] || SIZE_STYLES.md;
  const widthClass = fullWidth ? 'w-full' : '';

  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      className={`${baseClasses} ${variantClass} ${sizeClass} ${widthClass} ${className}`.trim()}
      {...props}
    >
      {isLoading ? (
        <>
          <Loader2 className="w-4 h-4 animate-spin shrink-0" />
          <span>{children}</span>
        </>
      ) : (
        <>
          {Icon && iconPosition === 'left' && <Icon className="w-4 h-4 shrink-0" />}
          {children}
          {Icon && iconPosition === 'right' && <Icon className="w-4 h-4 shrink-0" />}
        </>
      )}
    </button>
  );
}
