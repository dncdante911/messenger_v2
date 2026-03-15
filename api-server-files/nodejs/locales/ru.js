'use strict';

/**
 * Russian locale strings.
 * Keys used by auth.js, rating.js and any server-side user-facing messages.
 */
module.exports = {

    // ── OTP errors ─────────────────────────────────────────────────────────────
    otp_invalid:  (n) => `Неверный код. Осталось попыток: ${n}`,
    otp_too_many: 'Слишком много неверных попыток. Запросите новый код.',
    otp_locked:   'Код заблокирован. Запросите новый.',
    otp_expired:  'Код устарел. Запросите новый.',

    // ── Auth messages ──────────────────────────────────────────────────────────
    provide_email_or_phone:   'Укажите email или номер телефона',
    account_not_found:        'Аккаунт с таким email/телефоном не найден',
    account_not_found_short:  'Аккаунт не найден',
    code_sent:                'Код подтверждения отправлен',
    server_error:             'Ошибка сервера. Попробуйте позже.',
    provide_code_and_password: 'Укажите код и новый пароль',
    password_too_short:       'Пароль слишком короткий (мин. 6 символов)',
    password_updated:         'Пароль успешно изменён',
    update_password_error:    'Не удалось обновить пароль.',
    provide_code:             'Укажите код подтверждения',
    auth_success:             'Авторизация успешна',
    phone_format_error:       'Неверный формат телефона. Используйте международный формат: +79XXXXXXXXX',

    // ── SMS texts ──────────────────────────────────────────────────────────────
    sms_reset:    (code) => `WorldMates: ваш код восстановления пароля: ${code}. Действителен 10 минут.`,
    sms_register: (code) => `WorldMates: ваш код регистрации: ${code}. Действителен 10 минут.`,
    sms_login:    (code) => `WorldMates: ваш код для входа: ${code}. Действителен 10 минут.`,

    // ── Email subjects & titles ────────────────────────────────────────────────
    email_subject_reset:    'Восстановление доступа — WorldMates',
    email_subject_welcome:  'Добро пожаловать в WorldMates!',
    email_subject_login:    'Код для входа — WorldMates',
    email_title_reset:      'Восстановление доступа',
    email_title_welcome:    'Добро пожаловать!',
    email_title_login:      'Код для входа',
    email_body_reset:       'Ваш код подтверждения для восстановления доступа:',
    email_body_welcome:     'Ваш аккаунт WorldMates создан! Подтвердите кодом ниже:',
    email_body_login:       'Ваш одноразовый код для входа в WorldMates:',
    code_validity:          '10 минут',
    code_disclaimer:        'Если вы не делали этого запроса — просто проигнорируйте это письмо.',

    // ── Trust levels (rating.js) ───────────────────────────────────────────────
    trust_verified:  'Верифицирован',
    trust_trusted:   'Надёжный',
    trust_untrusted: 'Ненадёжный',
    trust_neutral:   'Нейтральный',

    // ── Rating actions ─────────────────────────────────────────────────────────
    rating_removed: 'Оценка снята',
    rating_updated: 'Оценка обновлена',
    rating_added:   'Оценка добавлена',
};
