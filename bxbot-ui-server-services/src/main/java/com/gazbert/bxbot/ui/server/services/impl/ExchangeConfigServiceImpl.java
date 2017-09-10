/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Gareth Jon Lynch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.gazbert.bxbot.ui.server.services.impl;

import com.gazbert.bxbot.ui.server.domain.bot.BotConfig;
import com.gazbert.bxbot.ui.server.domain.exchange.ExchangeConfig;
import com.gazbert.bxbot.ui.server.repository.local.BotConfigRepository;
import com.gazbert.bxbot.ui.server.repository.remote.ExchangeConfigRepository;
import com.gazbert.bxbot.ui.server.services.ExchangeConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Implementation of the Exchange config service.
 *
 *  TODO - add unit tests for unknown bot ids etc...
 *
 * @author gazbert
 */
@Service("exchangeConfigService")
@Transactional
@ComponentScan(basePackages = {"com.gazbert.bxbot.ui.server.repository"})
public class ExchangeConfigServiceImpl implements ExchangeConfigService {

    private static final Logger LOG = LogManager.getLogger();

    private final ExchangeConfigRepository exchangeConfigRepository;
    private final BotConfigRepository botConfigRepository;

    @Autowired
    public ExchangeConfigServiceImpl(ExchangeConfigRepository exchangeConfigRepository,
                                     BotConfigRepository botConfigRepository) {

        this.exchangeConfigRepository = exchangeConfigRepository;
        this.botConfigRepository = botConfigRepository;
    }

    @Override
    public ExchangeConfig getExchangeConfig(String botId) {

        LOG.info(() -> "About to fetch Exchange config for botId: " + botId);

        final BotConfig botConfig = botConfigRepository.findById(botId);

        // TODO - bot not found check - if botId is bad, we need to 404 immediately, not go remote... return empty ExchangeConfig

        return exchangeConfigRepository.get(botConfig);
    }

    @Override
    public ExchangeConfig updateExchangeConfig(String botId, ExchangeConfig exchangeConfig) {

        final BotConfig botConfig = botConfigRepository.findById(botId);

        // TODO - bot not found check - if botId is bad, we need to 404 immediately, not go remote... return empty ExchangeConfig

        LOG.info(() -> "About to update bot " + botId + " Exchange config: " + exchangeConfig);
        return exchangeConfigRepository.save(botConfig, exchangeConfig);
    }
}