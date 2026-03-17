#!/bin/bash
# Wrapper that ensures pm2-runtime starts from the correct directory.
# Needed because aaPanel may launch pm2-runtime without --cwd, causing
# Runtime4Docker.js to crash on Node.js 24+ when path.join(undefined, script)
# is called (undefined was previously silently coerced in older Node versions).

export PM2_HOME=/www/wwwroot/worldmates.club/nodejs/.pm2

cd /www/wwwroot/worldmates.club/nodejs

exec /www/server/nodejs/v24.14.0/lib/node_modules/pm2/bin/pm2-runtime \
  /www/wwwroot/worldmates.club/nodejs/ecosystem.config.js
