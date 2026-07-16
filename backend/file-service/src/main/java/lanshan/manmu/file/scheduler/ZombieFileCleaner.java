package lanshan.manmu.file.scheduler;

import lanshan.manmu.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Zombie 文件定时清理。
 * <p>清理条件：status=PENDING 且 created_at 超过 30 分钟。
 * <p>调度频率：每 5 分钟扫一次（fixedDelay：上次结束后等 5 分钟再执行，避免任务堆积）。
 */
@Slf4j
@Component
public class ZombieFileCleaner {

    private final FileService fileService;

    public ZombieFileCleaner(FileService fileService) {
        this.fileService = fileService;
    }

    @Scheduled(fixedDelay = 300_000)  // 5 分钟 = 300_000 ms
    public void cleanup() {
        try {
            fileService.cleanupZombieFiles();
        } catch (Exception e) {
            log.error("zombie 文件清理任务异常", e);
        }
    }
}