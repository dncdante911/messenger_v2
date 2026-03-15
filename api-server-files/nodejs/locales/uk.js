'use strict';

/**
 * Ukrainian locale strings.
 * Keys used by auth.js, rating.js and any server-side user-facing messages.
 */
module.exports = {

    // ── OTP errors ─────────────────────────────────────────────────────────────
    otp_invalid:  (n) => `Невірний код. Залишилось спроб: ${n}`,
    otp_too_many: 'Забагато невірних спроб. Запросіть новий код.',
    otp_locked:   'Код заблоковано. Запросіть новий.',
    otp_expired:  'Код прострочений. Запросіть новий.',

    // ── Auth messages ──────────────────────────────────────────────────────────
    provide_email_or_phone:   'Вкажіть email або номер телефону',
    account_not_found:        'Акаунт з таким email/телефоном не знайдено',
    account_not_found_short:  'Акаунт не знайдено',
    code_sent:                'Код підтвердження надіслано',
    server_error:             'Помилка сервера. Спробуйте пізніше.',
    provide_code_and_password: 'Вкажіть код та новий пароль',
    password_too_short:       'Пароль надто короткий (мін. 6 символів)',
    password_updated:         'Пароль успішно змінено',
    update_password_error:    'Не вдалося оновити пароль.',
    provide_code:             'Вкажіть код підтвердження',
    auth_success:             'Авторизація успішна',
    phone_format_error:       'Невірний формат телефону. Використовуйте міжнародний формат: +380XXXXXXXXX',

    // ── SMS texts ──────────────────────────────────────────────────────────────
    sms_reset:    (code) => `WorldMates: ваш код відновлення пароля: ${code}. Дійсний 10 хвилин.`,
    sms_register: (code) => `WorldMates: ваш код реєстрації: ${code}. Дійсний 10 хвилин.`,
    sms_login:    (code) => `WorldMates: ваш код для входу: ${code}. Дійсний 10 хвилин.`,

    // ── Email subjects & titles ────────────────────────────────────────────────
    email_subject_reset:    'Відновлення доступу — WorldMates',
    email_subject_welcome:  'Ласкаво просимо до WorldMates!',
    email_subject_login:    'Код для входу — WorldMates',
    email_title_reset:      'Відновлення доступу',
    email_title_welcome:    'Ласкаво просимо!',
    email_title_login:      'Код для входу',
    email_body_reset:       'Ваш код підтвердження для відновлення доступу:',
    email_body_welcome:     'Ваш акаунт WorldMates створено! Підтвердіть кодом нижче:',
    email_body_login:       'Ваш одноразовий код для входу до WorldMates:',
    code_validity:          '10 хвилин',
    code_disclaimer:        'Якщо ви не робили цього запиту — просто проігноруйте це повідомлення.',

    // ── Trust levels (rating.js) ───────────────────────────────────────────────
    trust_verified:  'Верифікований',
    trust_trusted:   'Надійний',
    trust_untrusted: 'Ненадійний',
    trust_neutral:   'Нейтральний',

    // ── Rating actions ─────────────────────────────────────────────────────────
    rating_removed: 'Оцінку знято',
    rating_updated: 'Оцінку оновлено',
    rating_added:   'Оцінку додано',
};
