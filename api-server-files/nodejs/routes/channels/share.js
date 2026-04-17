'use strict';

/**
 * GET /share/channel/:channelId/post/:postId
 *
 * Returns an HTML page with Open Graph meta tags so that messaging apps
 * (Telegram, WhatsApp, iMessage, etc.) generate a rich link preview when
 * a user pastes the URL.  The page also redirects to the app deep-link after
 * a short delay so that users who tap the link on mobile can open the app.
 */

const funcs = require('../../functions/functions');

function escapeHtml(str) {
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
                    attributes: ['page_title', 'page_description', 'page_picture'],
                    raw: true
                }),
                ctx.wo_posts.findOne({
                    where: { id: postId },
                    raw: true
                })
            ]);

            if (!channel || !post) {
                return res.status(404).send('Not found');
            }

            const channelName = channel.page_title || 'WorldMates Channel';
            const rawDesc     = (post.text || '').replace(/<[^>]+>/g, '').trim();
            const description = rawDesc.length > 200
                ? rawDesc.slice(0, 197) + '…'
                : (rawDesc || channelName);

            const ogTitle = escapeHtml(`${channelName}`);
            const ogDesc  = escapeHtml(description);

            // Resolve image: prefer first post media, fall back to channel avatar
            let ogImage = '';
            if (post.postFile) {
                ogImage = await funcs.Wo_GetMedia(ctx, post.postFile);
            } else if (post.media) {
                ogImage = await funcs.Wo_GetMedia(ctx, post.media);
            } else if (channel.page_picture) {
                ogImage = await funcs.Wo_GetMedia(ctx, channel.page_picture);
            }

            const pageUrl  = `${ctx.globalconfig.site_url || 'https://worldmates.club'}`;
            const nodeBase = `https://worldmates.club:449`;
            const ogUrl    = `${nodeBase}/share/channel/${channelId}/post/${postId}`;
            const deepLink = `worldmates://channel/${channelId}/post/${postId}`;

            const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${ogTitle}</title>
<!-- Open Graph -->
<meta property="og:type"        content="article">
<meta property="og:site_name"   content="WorldMates">
<meta property="og:title"       content="${ogTitle}">
<meta property="og:description" content="${ogDesc}">
<meta property="og:url"         content="${escapeHtml(ogUrl)}">
${ogImage ? `<meta property="og:image" content="${escapeHtml(ogImage)}">` : ''}
<!-- Twitter Card -->
<meta name="twitter:card"        content="summary_large_image">
<meta name="twitter:title"       content="${ogTitle}">
<meta name="twitter:description" content="${ogDesc}">
${ogImage ? `<meta name="twitter:image" content="${escapeHtml(ogImage)}">` : ''}
<style>
  body { font-family: -apple-system, sans-serif; background: #0f0f1a; color: #e0e0e0;
         display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
  .card { max-width: 480px; padding: 32px; background: #1a1a2e; border-radius: 16px;
          box-shadow: 0 8px 32px rgba(0,0,0,0.4); text-align: center; }
  h1 { font-size: 1.4rem; margin-bottom: 8px; color: #fff; }
  p  { font-size: 0.9rem; color: #a0a0c0; margin-bottom: 24px; line-height: 1.5; }
  a.btn { display: inline-block; padding: 12px 28px; background: #5b5ef7;
          color: #fff; border-radius: 10px; text-decoration: none; font-weight: 600; }
</style>
</head>
<body>
<div class="card">
  <h1>${ogTitle}</h1>
  <p>${ogDesc}</p>
  <a class="btn" href="${escapeHtml(deepLink)}">Open in WorldMates</a>
</div>
<script>
  // Attempt to open the app; if the page is still visible after 1.5 s the app
  // is not installed — stay on this page so the user can at least read the preview.
  setTimeout(function() {
    window.location.href = "${escapeHtml(deepLink)}";
  }, 100);
</script>
</body>
</html>`;

            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            // Allow OG crawlers to cache for 10 min
            res.setHeader('Cache-Control', 'public, max-age=600');
            return res.send(html);
        } catch (err) {
            console.error('[Share/channel-post]', err.message);
            return res.status(500).send('Server error');
        }
    });
}

module.exports = { registerShareRoutes };
