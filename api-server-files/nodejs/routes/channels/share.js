'use strict';

/**
 * GET /share/channel/:channelId/post/:postId
 *
 * Returns an HTML page with Open Graph meta tags so that messaging apps
 * (Telegram, WhatsApp, iMessage, etc.) generate a rich link preview when
 * a user pastes the URL. The page also attempts a deep-link redirect so
 * that users who tap the link on mobile can open the app directly.
 */

const funcs = require('../../functions/functions');

function esc(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function registerShareRoutes(app, ctx) {
    app.get('/share/channel/:channelId/post/:postId', async (req, res) => {
        try {
            const channelId = parseInt(req.params.channelId);
            const postId    = parseInt(req.params.postId);

            if (!channelId || !postId) {
                return res.status(400).send('Invalid share link');
            }

            const [channel, post] = await Promise.all([
                ctx.wo_pages.findOne({
                    where: { page_id: channelId },
                    attributes: ['page_title', 'page_description', 'avatar'],
                    raw: true
                }),
                ctx.wo_posts.findOne({
                    where: { id: postId },
                    attributes: ['id', 'postText', 'postFile', 'postFileThumb', 'page_id'],
                    raw: true
                })
            ]);

            if (!channel || !post) {
                return res.status(404).send('Not found');
            }

            const channelName = channel.page_title || 'WorldMates';

            // Strip HTML tags and trim whitespace from post text
            const rawText = (post.postText || '').replace(/<[^>]+>/g, '').trim();
            const excerpt = rawText.length > 220
                ? rawText.slice(0, 217) + '…'
                : rawText;

            const ogTitle = esc(channelName);
            const ogDesc  = esc(excerpt || channel.page_description || channelName);

            // Image: prefer post thumbnail, then post file, then channel avatar
            let ogImage = '';
            const imgSource = post.postFileThumb || post.postFile || null;
            if (imgSource && imgSource.trim() !== '') {
                ogImage = await funcs.Wo_GetMedia(ctx, imgSource);
            } else if (channel.avatar && channel.avatar.trim() !== '') {
                ogImage = await funcs.Wo_GetMedia(ctx, channel.avatar);
            }

            const nodeBase = 'https://worldmates.club:449';
            const ogUrl    = `${nodeBase}/share/channel/${channelId}/post/${postId}`;
            const deepLink = `worldmates://channel/${channelId}/post/${postId}`;

            // Channel avatar for display in the landing page
            let channelAvatarUrl = '';
            if (channel.avatar && channel.avatar.trim() !== '') {
                channelAvatarUrl = await funcs.Wo_GetMedia(ctx, channel.avatar);
            }

            const avatarHtml = channelAvatarUrl
                ? `<img class="ch-avatar" src="${esc(channelAvatarUrl)}" alt="${ogTitle}">`
                : `<div class="ch-avatar ch-avatar-placeholder">${esc(channelName.charAt(0).toUpperCase())}</div>`;

            const previewImgHtml = ogImage
                ? `<div class="preview-img-wrap"><img class="preview-img" src="${esc(ogImage)}" alt=""></div>`
                : '';

            const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${ogTitle} — WorldMates</title>
<!-- Open Graph -->
<meta property="og:type"        content="article">
<meta property="og:site_name"   content="WorldMates">
<meta property="og:title"       content="${ogTitle}">
<meta property="og:description" content="${ogDesc}">
<meta property="og:url"         content="${esc(ogUrl)}">
${ogImage ? `<meta property="og:image"       content="${esc(ogImage)}">
<meta property="og:image:width"  content="1200">
<meta property="og:image:height" content="630">` : ''}
<!-- Twitter Card -->
<meta name="twitter:card"        content="${ogImage ? 'summary_large_image' : 'summary'}">
<meta name="twitter:site"        content="@worldmates">
<meta name="twitter:title"       content="${ogTitle}">
<meta name="twitter:description" content="${ogDesc}">
${ogImage ? `<meta name="twitter:image" content="${esc(ogImage)}">` : ''}
<style>
  *, *::before, *::after { box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #0d0d1a;
    color: #e2e2f0;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    margin: 0;
    padding: 16px;
  }
  .card {
    width: 100%;
    max-width: 440px;
    background: #16162a;
    border: 1px solid rgba(255,255,255,0.07);
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 16px 48px rgba(0,0,0,0.5);
  }
  .preview-img-wrap { width: 100%; aspect-ratio: 16/9; overflow: hidden; background: #0d0d1a; }
  .preview-img { width: 100%; height: 100%; object-fit: cover; display: block; }
  .card-body { padding: 24px 24px 28px; }
  .channel-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 14px;
  }
  .ch-avatar {
    width: 40px; height: 40px; border-radius: 50%;
    object-fit: cover; flex-shrink: 0;
  }
  .ch-avatar-placeholder {
    width: 40px; height: 40px; border-radius: 50%;
    background: linear-gradient(135deg, #5b5ef7, #a855f7);
    display: flex; align-items: center; justify-content: center;
    font-size: 18px; font-weight: 700; color: #fff; flex-shrink: 0;
  }
  .channel-name {
    font-size: 14px;
    font-weight: 600;
    color: #a0a0f0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .wm-badge {
    margin-left: auto;
    font-size: 10px;
    background: rgba(91,94,247,0.2);
    color: #8080f8;
    border-radius: 6px;
    padding: 2px 8px;
    white-space: nowrap;
    flex-shrink: 0;
  }
  .post-text {
    font-size: 15px;
    line-height: 1.6;
    color: #d8d8ee;
    margin: 0 0 20px;
    display: -webkit-box;
    -webkit-line-clamp: 5;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    padding: 14px;
    background: linear-gradient(135deg, #5b5ef7, #7c3aed);
    color: #fff;
    border-radius: 12px;
    text-decoration: none;
    font-size: 15px;
    font-weight: 600;
    transition: opacity .15s;
  }
  .btn:hover { opacity: .88; }
  .btn svg { width: 18px; height: 18px; fill: currentColor; flex-shrink: 0; }
  .sub { margin-top: 12px; text-align: center; font-size: 12px; color: #666; }
</style>
</head>
<body>
<div class="card">
  ${previewImgHtml}
  <div class="card-body">
    <div class="channel-row">
      ${avatarHtml}
      <span class="channel-name">${ogTitle}</span>
      <span class="wm-badge">WorldMates</span>
    </div>
    ${excerpt ? `<p class="post-text">${esc(excerpt)}</p>` : ''}
    <a class="btn" href="${esc(deepLink)}" id="openBtn">
      <svg viewBox="0 0 24 24"><path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8l8 5 8-5v10zm-8-7L4 6h16l-8 5z"/></svg>
      Открыть в WorldMates
    </a>
    <p class="sub">Нет приложения? <a href="https://play.google.com/store/apps/details?id=com.worldmates.messenger" style="color:#8080f8;">Скачать</a></p>
  </div>
</div>
<script>
  // Try to open the app immediately via deep link;
  // if the browser stays on this page the app is not installed.
  setTimeout(function() {
    document.getElementById('openBtn').click();
  }, 300);
</script>
</body>
</html>`;

            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.setHeader('Cache-Control', 'public, max-age=600');
            return res.send(html);
        } catch (err) {
            console.error('[Share/channel-post]', err.message);
            return res.status(500).send('Server error');
        }
    });
}

module.exports = { registerShareRoutes };
