#!/usr/bin/env bash
mysql -B -N -u CT --password=wewillretire@2019 -h cryptotraderdb -D CT -e "
select concat(symbol,',',
    `interval`,',',
    openTime,',',
    closeTime,',',
    openPrice,',',
    highPrice,',',
    lowPrice,',',
    closePrice,',',
    `volume`
) as result
from CT.BINANCE_CANDLE
where symbol='$1'
and `closeTime` >= unix_timestamp('$2')*1000
and `closeTime` < unix_timestamp('$3' + interval 1 day)*1000;
"