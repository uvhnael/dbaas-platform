'use client';

import { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { Database, Eye, EyeOff, Loader2, Check, X } from 'lucide-react';

// Validation rules matching backend RegisterRequest
const USERNAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]*$/;
const PASSWORD_MIN_LENGTH = 8;

interface ValidationRule {
  label: string;
  test: (value: string) => boolean;
}

const usernameRules: ValidationRule[] = [
  { label: 'Ít nhất 3 ký tự', test: (v) => v.length >= 3 },
  { label: 'Bắt đầu bằng chữ cái', test: (v) => /^[a-zA-Z]/.test(v) },
  { label: 'Chỉ chứa chữ, số và _', test: (v) => USERNAME_REGEX.test(v) || v.length === 0 },
];

const passwordRules: ValidationRule[] = [
  { label: 'Ít nhất 8 ký tự', test: (v) => v.length >= PASSWORD_MIN_LENGTH },
  { label: 'Có chữ thường (a-z)', test: (v) => /[a-z]/.test(v) },
  { label: 'Có chữ hoa (A-Z)', test: (v) => /[A-Z]/.test(v) },
  { label: 'Có số (0-9)', test: (v) => /\d/.test(v) },
];

function ValidationIndicator({ rules, value }: { rules: ValidationRule[]; value: string }) {
  if (!value) return null;
  
  return (
    <div className="mt-2 space-y-1">
      {rules.map((rule, i) => {
        const passed = rule.test(value);
        return (
          <div key={i} className="flex items-center gap-2 text-xs">
            {passed ? (
              <Check className="w-3 h-3 text-emerald-400" />
            ) : (
              <X className="w-3 h-3 text-red-400" />
            )}
            <span className={passed ? 'text-emerald-400' : 'text-zinc-500'}>
              {rule.label}
            </span>
          </div>
        );
      })}
    </div>
  );
}

export default function RegisterPage() {
  const router = useRouter();
  const { register, isLoading: isAuthLoading } = useAuth();
  
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isUsernameValid = useMemo(() => 
    usernameRules.every(rule => rule.test(username)), [username]);
  
  const isPasswordValid = useMemo(() => 
    passwordRules.every(rule => rule.test(password)), [password]);

  const isFormValid = useMemo(() => 
    isUsernameValid && isPasswordValid && password === confirmPassword && confirmPassword.length > 0,
    [isUsernameValid, isPasswordValid, password, confirmPassword]
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!isUsernameValid) {
      setError('Username không hợp lệ');
      return;
    }

    if (!isPasswordValid) {
      setError('Mật khẩu không đáp ứng yêu cầu');
      return;
    }

    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp');
      return;
    }

    setIsSubmitting(true);

    try {
      await register({ username, password, email: email || undefined });
      toast.success('Đăng ký thành công!');
      router.push('/');
    } catch (err: any) {
      setError(err.message || 'Đăng ký thất bại');
      toast.error('Đăng ký thất bại');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-950 p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-600 mb-4">
            <Database className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-white">DBaaS Platform</h1>
          <p className="text-zinc-500 mt-2">Tạo tài khoản mới</p>
        </div>

        {/* Register Form */}
        <div className="glass-card rounded-2xl p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            {error && (
              <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                {error}
              </div>
            )}

            {/* Username */}
            <div className="space-y-2">
              <label htmlFor="username" className="text-sm font-medium text-zinc-300">
                Tên đăng nhập <span className="text-red-400">*</span>
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                className={`w-full px-4 py-3 rounded-lg bg-zinc-900/50 border text-white placeholder-zinc-500 focus:ring-1 focus:ring-emerald-500/20 transition-all ${
                  username && !isUsernameValid 
                    ? 'border-red-500/50 focus:border-red-500/50' 
                    : username && isUsernameValid
                      ? 'border-emerald-500/50 focus:border-emerald-500/50'
                      : 'border-white/10 focus:border-emerald-500/50'
                }`}
                placeholder="username"
                required
                autoFocus
              />
              <ValidationIndicator rules={usernameRules} value={username} />
            </div>

            {/* Email */}
            <div className="space-y-2">
              <label htmlFor="email" className="text-sm font-medium text-zinc-300">
                Email <span className="text-zinc-600">(tùy chọn)</span>
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                className="w-full px-4 py-3 rounded-lg bg-zinc-900/50 border border-white/10 text-white placeholder-zinc-500 focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/20 transition-all"
                placeholder="email@example.com"
              />
            </div>

            {/* Password */}
            <div className="space-y-2">
              <label htmlFor="password" className="text-sm font-medium text-zinc-300">
                Mật khẩu <span className="text-red-400">*</span>
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
                  className={`w-full px-4 py-3 pr-12 rounded-lg bg-zinc-900/50 border text-white placeholder-zinc-500 focus:ring-1 focus:ring-emerald-500/20 transition-all ${
                    password && !isPasswordValid 
                      ? 'border-red-500/50 focus:border-red-500/50' 
                      : password && isPasswordValid
                        ? 'border-emerald-500/50 focus:border-emerald-500/50'
                        : 'border-white/10 focus:border-emerald-500/50'
                  }`}
                  placeholder="••••••••"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-300 transition-colors"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" aria-hidden="true" /> : <Eye className="w-5 h-5" aria-hidden="true" />}
                </button>
              </div>
              <ValidationIndicator rules={passwordRules} value={password} />
            </div>

            {/* Confirm Password */}
            <div className="space-y-2">
              <label htmlFor="confirmPassword" className="text-sm font-medium text-zinc-300">
                Xác nhận mật khẩu <span className="text-red-400">*</span>
              </label>
              <input
                id="confirmPassword"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                className={`w-full px-4 py-3 rounded-lg bg-zinc-900/50 border text-white placeholder-zinc-500 focus:ring-1 focus:ring-emerald-500/20 transition-all ${
                  confirmPassword && password !== confirmPassword 
                    ? 'border-red-500/50 focus:border-red-500/50' 
                    : confirmPassword && password === confirmPassword
                      ? 'border-emerald-500/50 focus:border-emerald-500/50'
                      : 'border-white/10 focus:border-emerald-500/50'
                }`}
                placeholder="••••••••"
                required
              />
              {confirmPassword && password !== confirmPassword && (
                <div className="flex items-center gap-2 text-xs text-red-400 mt-1">
                  <X className="w-3 h-3" />
                  <span>Mật khẩu không khớp</span>
                </div>
              )}
              {confirmPassword && password === confirmPassword && (
                <div className="flex items-center gap-2 text-xs text-emerald-400 mt-1">
                  <Check className="w-3 h-3" />
                  <span>Mật khẩu khớp</span>
                </div>
              )}
            </div>

            <Button
              type="submit"
              disabled={isSubmitting || isAuthLoading || !isFormValid}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 disabled:bg-zinc-700 disabled:cursor-not-allowed text-white font-medium rounded-lg transition-colors"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Đang đăng ký...
                </>
              ) : (
                'Đăng ký'
              )}
            </Button>
          </form>

          <div className="mt-6 text-center">
            <span className="text-zinc-500 text-sm">Đã có tài khoản? </span>
            <Link href="/login" className="text-emerald-400 hover:text-emerald-300 text-sm font-medium transition-colors">
              Đăng nhập
            </Link>
          </div>
        </div>

        {/* Hint */}
        <div className="mt-4 text-center text-xs text-zinc-600">
          <p>Ví dụ password hợp lệ: Admin123</p>
        </div>
      </div>
    </div>
  );
}
