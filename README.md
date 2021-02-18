# Arbitrader
![AWS CodeBuild](https://codebuild.us-west-2.amazonaws.com/badges?uuid=eyJlbmNyeXB0ZWREYXRhIjoiUnhycTV0MEFLb293K1Y0QlBjOUxESnBaWXM3V3BLMGhEU2Zjcm0yWHpnRGhFdmxYWm45M2dqVU1ZUjRSdldhR0NsUEYyWk0xVWJZUVZBUXhGZmJUZjhrPSIsIml2UGFyYW1ldGVyU3BlYyI6InNER0tuUnhKQ0pUMVhTUVAiLCJtYXRlcmlhbFNldFNlcmlhbCI6MX0%3D&branch=master)
[![Dependabot Status](https://api.dependabot.com/badges/status?host=github&repo=agonyforge/arbitrader)](https://dependabot.com)    
[![Discord](https://img.shields.io/discord/767482695323222036?logo=Discord)](https://discord.gg/ZYmG3Nw)
[![Ko-fi Blog](https://img.shields.io/badge/Blog-Ko--fi-informational?logo=Ko-fi)](https://ko-fi.com/scionaltera)  
**A market neutral cryptocurrency trading bot.**

## What is it?
Arbitrader is a program that finds trading opportunities between two different cryptocurrency exchanges and performs automatic low-risk trades.

The software is MIT licensed, so you are free to use it as you wish. Please keep in mind that trading cryptocurrencies carries a lot of inherent risk, and unattended automated trading can add even more risk. The author(s) of Arbitrader are not responsible if you lose money using this bot. Please be patient and careful!

## How do I use it?
Please see the [wiki](https://github.com/agonyforge/arbitrader/wiki) for detailed instructions on running and using Arbitrader.

## How can I help out?
I'm trying to build up a little community around Arbitrader. We have a [Discord](https://discord.gg/ZYmG3Nw) server and a blog over at [Ko-fi](https://ko-fi.com/scionaltera). If you'd like to meet some of the people who work on it and use it, or if you'd like to provide some feedback or support, it would mean a lot to me if you'd drop in and say hello.

Code contributions are always welcome! Please see [CONTRIBUTING.md](https://github.com/agonyforge/arbitrader/blob/master/CONTRIBUTING.md) for details on how to contribute.

## How does it work?
The trading strategy itself is inspired by [Blackbird](https://github.com/butor/blackbird). Arbitrader uses none of Blackbird's code, but a lot of the algorithms are similar.

Arbitrader strives to be "market neutral". That is to say, it enters a long position on one exchange and a short position on the other. Assuming the prices on both exchanges were exactly the same, the result would be that no matter how the markets moved, you could exit both positions simultaneously and break even (not considering fees). Market neutrality is very desirable in crypto because the markets tend to be so volatile.

**So how does it make any money, if it's "market neutral"?**

The reality is that markets don't always have the same prices. Arbitrader waits until one market is higher in price than the other before executing the trades. It enters a short position on the higher priced exchange and a long position on the lower exchange. As the prices converge or even reverse, one trade will generally make a profit and the other will lose a little.

In the configuration, you can specify via the "spread" how much different the markets need to be to enter a trade and how much they need to converge to make up the profit you'd like. The default entry spread is 0.008, or 0.8%, meaning the two markets have to be 0.8% different from one another. The exit target is 0.005, or 0.5%, meaning the markets need to come back together by 0.5% before it will exit the trades.

Please be aware that Arbitrader will add the exchange fees onto the exit target when it calculates the actual prices. The 0.5% is just the profit part. A typical exchange might add another 0.2% for each trade, so the actual exit target would be 0.5% + (0.2% * 4) = 1.3%. If you started at 0.8% that means the markets have to reverse one another by 0.5% before it will exit the trade.

It is very easy to choose entry and exit targets that result in the bot never making trades, or worse, entering trades but never exiting them. The "right" values to use there are going to vary by which currencies you trade, which exchanges you use, and your personal ability to wait on the bot to do stuff. It is normal to need to experiment and adjust those values from time to time as market conditions change.

**Wait, "short"? What is that?**

Most exchanges let you take one currency, such as USD, and use it to buy another currency, like BTC. You have to have some USD in your account and you make a simple trade with someone for the BTC, and that's the end of your transaction. The bet you're making is that the value of BTC will go up, so that's what you want to be holding when it does.

"Shorting" gives you the option to make the bet that BTC will drop in value, even when you don't hold any BTC in your account.

It works like this:

1. You only have USD in your account, but you place an order to sell BTC for more USD. That's a "short" sale.
1. Behind the scenes, you are borrowing some BTC from the exchange and immediately selling it on the market for some USD.
1. When you close your trade you are buying back the BTC at the current market price and giving it back to the exchange plus a little interest. That closes your position and satisfies the loan you had with the exchange.

If the BTC price went down, you don't need to spend all the USD to buy back the BTC and you get to keep the difference.

If the BTC price went up, it will cost more to buy it back than what you made by selling the borrowed BTC in the first place. You have to contribute the difference out of your own funds so you can pay the BTC back to the exchange.

**Just how much money are we talking about here?**

The gains from each trade tend to be modest, because the prices across exchanges don't tend to vary very widely. The benefit is that the risk is so low. Each pair of trades has a very high probability of making a small profit, and there is almost no impact from big market shifts.

Compare that to opening just a single position. Sure, if it goes your way then you can make a lot more profit. If it doesn't go your way, then you stand to lose just as much. This strategy is nice because you're no longer betting on whether the price will go up or down: you're betting that divergent prices will tend to converge, and that tends to be a much safer bet.

**You're saying it can't lose, then?**

Not quite. It is possible that two exchanges have prices that are always divergent from one another. If the exchange with the higher price is always higher and never dips below the second exchange's price then the bot could open a pair of trades and never be able to close it profitably.

It is also possible to configure the "spread", or the amount of difference between exchange prices, too narrowly or for the exchange fees to cost too much. You might want to narrow the entry and exit spreads to make the bot trade more frequently, for example. In those cases you might see your trades close, but the sum of both trades would be negative and you would have lost money overall.

A third scenario is if your short order runs out of *margin*. Short orders are borrowing from the exchange, so they require you to keep some percentage of USD in your account as collateral. If your trade stays open and goes too far against you, the exchange will force your trade to close and you will end up paying most of the money out of your account to cover the difference. It's harsh, but the alternative would be for them to leave your trade open and send you a bill for money above and beyond what's in your account!

**I'd like to see an example, please.**

Ok, let's say there are two exchanges. We have a USD balance in both, and the price of COIN is $1000.00 on both exchanges. There is no opportunity here yet, since there is no difference between the prices. We'll wait for the prices to change.

```
          FakeCoins   CryptoTrade
COIN/USD: 1000.00     1000.00
```

Sooner or later, the prices diverge enough to catch the bot's attention.

```
          FakeCoins   CryptoTrade
COIN/USD: 950.00      1050.00
```

Now we're talking. We'll place a short order for $1000 on CryptoTrade and a long order for $1000 on FakeCoins, because we expect the higher price to go down and the lower price to go up until they match.

```
          FakeCoins   CryptoTrade
COIN/USD: 950.00      1050.00
          (bought 1.05 COIN for $1000)
                      (sold 0.95 COIN for $1000)
```

Suddenly the markets both jump up by $500! Somebody is pumping a ton of money into COIN/USD! Due to our market neutral strategy, we gained money on FakeCoins and lost the same amount of money on CryptoTrade. If the price had dropped by $500 instead, the opposite would have happened. The two trades balance each other out, so everything is fine.

```
          FakeCoins   CryptoTrade
COIN/USD: 1450.00     1550.00
          (long 1.05 COIN)
                      (short 0.95 COIN)
```

Finally, the prices converge and we can exit the trade.

```
          FakeCoins   CryptoTrade
COIN/USD: 1550.00     1550.00
          (sold 1.05 COIN @ $1550.00 for $1627.50)
                      (bought 0.95 COIN @ 1550.00 for $1472.50)
```

So what happened? Did we win?  

```
$1627.50 (sold) - $1000.00 (bought) = $627.50 profit on our long FakeCoins trade.  
$1000.00 (sold) - $1472.50 (bought) = -$472.50 loss on our short CryptoTrade trade.  
$627.50 - $472.50 = $155.00 total profit, minus any fees.  
```

We survived an unexpected major market shift and came out ahead in the end. Not bad!

Obviously this is a contrived example with fake numbers. Your mileage will vary, but hopefully that illustrates the principle at work in the strategy. If you still aren't convinced, Blackbird has an [issue thread](https://github.com/butor/blackbird/issues/100) that goes into a huge amount of detail and is well worth the read.
