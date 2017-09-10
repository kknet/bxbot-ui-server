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
import com.gazbert.bxbot.ui.server.repository.local.BotConfigRepository;
import com.gazbert.bxbot.ui.server.services.BotConfigService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Implementation of the Bot config service.
 *
 * @author gazbert
 */
@Service("botConfigService")
@Transactional
@ComponentScan(basePackages = {"com.gazbert.bxbot.repository"})
public class BotConfigServiceImpl implements BotConfigService {

    private static final Logger LOG = LogManager.getLogger();
    private final BotConfigRepository botConfigRepository;

    @Autowired
    public BotConfigServiceImpl(BotConfigRepository botConfigRepository) {
        this.botConfigRepository = botConfigRepository;
    }

    @Override
    public List<BotConfig> getAllBotConfig() {
        return botConfigRepository.findAll();
    }

    @Override
    public BotConfig getBotConfig(String id) {
        LOG.info(() -> "Fetching Bot config for id: " + id);
        return botConfigRepository.findById(id);
    }

    @Override
    public BotConfig updateBotConfig(BotConfig config) {
        LOG.info(() -> "About to update Bot config: " + config);
        return botConfigRepository.save(config);
    }

    @Override
    public BotConfig createBotConfig(BotConfig config) {
        LOG.info(() -> "About to create Bot config: " + config);
        return botConfigRepository.save(config);
    }

    @Override
    public BotConfig deleteBotConfig(String id) {
        LOG.info(() -> "About to delete Bot config for id: " + id);
        return botConfigRepository.delete(id);
    }
}