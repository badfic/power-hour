package com.badfic.powerhour;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PowerHourConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Value("${BOT_TOKEN}")
    private String botToken;

    @Value("${OWNER_ID}")
    public String ownerId;

    @Bean
    public ExecutorService executorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public TaskExecutor applicationTaskExecutor(final ExecutorService executorService) {
        return new TaskExecutorAdapter(executorService);
    }

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        final var threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setThreadFactory(Thread.ofVirtual().factory());
        threadPoolTaskScheduler.setRejectedExecutionHandler((runnable, executor) -> {
            log.error("Rejected task in taskScheduler. [runnable={}]", runnable);
        });
        threadPoolTaskScheduler.setErrorHandler(t -> {
            log.error("Error in scheduled task", t);
        });
        threadPoolTaskScheduler.initialize();
        threadPoolTaskScheduler.setPoolSize(4);
        return threadPoolTaskScheduler;
    }

    @Bean
    public OkHttpClient okHttpClient(final ExecutorService executorService) {
        final var dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequestsPerHost(25);
        final var connectionPool = new ConnectionPool(4, 10, TimeUnit.SECONDS);

        //noinspection KotlinInternalInJava
        return new OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .dispatcher(dispatcher)
                .build();
    }

    @Bean
    public AudioPlayerManager audioPlayerManager() {
        final var manager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerLocalSource(manager);
        return manager;
    }

    @Bean
    public JDA jda(final ThreadPoolTaskScheduler taskScheduler,
                   final ExecutorService executorService,
                   final OkHttpClient okHttpClient,
                   final MessageListener messageListener) {
        final var intents = List.of(GatewayIntent.GUILD_VOICE_STATES);

        return JDABuilder.create(botToken, intents)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOJI, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.FORUM_TAGS,
                        CacheFlag.ROLE_TAGS, CacheFlag.SCHEDULED_EVENTS, CacheFlag.STICKER, CacheFlag.MEMBER_OVERRIDES)
                .setRateLimitScheduler(taskScheduler.getScheduledExecutor(), false)
                .setRateLimitElastic(executorService, false)
                .setCallbackPool(executorService, false)
                .setEventPool(executorService, false)
                .setGatewayPool(taskScheduler.getScheduledExecutor(), false)
                .setHttpClient(okHttpClient)
                .addEventListeners(messageListener)
                .setAudioPool(taskScheduler.getScheduledExecutor(), false)
                .setActivity(Activity.playing("Power Hour"))
                .build();
    }
}
