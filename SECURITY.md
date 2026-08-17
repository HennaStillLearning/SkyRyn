# Security policy

SkyRyn runs on the client only. It writes to `.minecraft/config` and makes a single
outgoing request, to Hypixel's public bazaar endpoint, without an API key and without
any account data.

## Reporting a problem

If you find a security issue, please report it privately through GitHub:
open the **Security** tab and use **Report a vulnerability**. That keeps the details
out of public view until there is a fix.

For anything that is not a security issue, open a normal
[issue](https://github.com/HennaStillLearning/SkyRyn/issues).

## Supported versions

The latest release is supported. Older versions do not receive fixes.

## A note on downloads

Only download SkyRyn from [Modrinth](https://modrinth.com/mod/skyryn) or from the
[releases](https://github.com/HennaStillLearning/SkyRyn/releases) page here. The mod can
send chat commands on your behalf (`/bz`, `/hb` and party messages), so a modified build
from elsewhere could misuse that.
