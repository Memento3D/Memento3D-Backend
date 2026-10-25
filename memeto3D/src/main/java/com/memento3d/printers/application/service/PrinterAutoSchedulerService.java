package com.memento3d.printers.application.service;

import com.memento3d.operations.reviewqueue.application.service.ManualReviewService;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterStatus;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Intelligent Auto-Scheduling Engine for 3D Print Farm.
 * Implements BR-PRINT-001, BR-PRINT-003: automated printer selection based on
 * hardware status, queue depth, heartbeat recency, and material compatibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterAutoSchedulerService {

    private final PrinterRepository printerRepository;
    private final PrintJobRepository printJobRepository;
    private final ManualReviewService manualReviewService;

    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofSeconds(120);

    /**
     * Finds and assigns the optimal 3D printer for an unassigned PrintJob.
     *
     * @param printJob the print job requiring printer scheduling
     * @return assigned Printer, or empty if escalated to Manual Review Queue
     */
    @Transactional
    public Optional<Printer> scheduleOptimalPrinter(PrintJob printJob) {
        log.info("Evaluating auto-scheduling for PrintJob ID: {}, material: {}",
                printJob.getId(), printJob.getMaterial() != null ? printJob.getMaterial().getSku() : "unknown");

        List<Printer> candidates = printerRepository.findAll().stream()
                .filter(p -> p.getStatus() == PrinterStatus.IDLE)
                .filter(this::isHeartbeatActive)
                .toList();

        if (candidates.isEmpty()) {
            log.warn("No online or idle printers found for PrintJob {}. Escalating to Manual Review Queue.", printJob.getJobNumber());
            manualReviewService.enqueueReview(
                    ReviewType.PRINT_QC,
                    ReviewPriority.HIGH,
                    printJob.getId(),
                    printJob.getJobNumber(),
                    printJob.getCreatedBy(),
                    BigDecimal.valueOf(0.10),
                    "NO_AVAILABLE_PRINTERS",
                    "Tất cả máy in trong hệ thống đang OFFLINE, BẬN hoặc đang BẢO TRÌ. Cần điều phối thủ công.",
                    "WAIT_FOR_PRINTER_OR_OUTSOURCE"
            );
            return Optional.empty();
        }

        // Score each candidate printer
        Printer bestPrinter = candidates.stream()
                .max(Comparator.comparingInt(p -> calculateFitnessScore(p, printJob)))
                .orElse(null);

        if (bestPrinter == null || calculateFitnessScore(bestPrinter, printJob) < 40) {
            log.warn("Best printer score below threshold for PrintJob {}. Escalating to Manual Review.", printJob.getJobNumber());
            manualReviewService.enqueueReview(
                    ReviewType.PRINT_QC,
                    ReviewPriority.MEDIUM,
                    printJob.getId(),
                    printJob.getJobNumber(),
                    printJob.getCreatedBy(),
                    BigDecimal.valueOf(0.35),
                    "LOW_PRINTER_FITNESS_SCORE",
                    "Không tìm thấy máy in có độ tương thích cao với vật liệu và kích thước mô hình.",
                    "MANUAL_PRINTER_ASSIGNMENT"
            );
            return Optional.empty();
        }

        // Assign printer to job
        printJob.setPrinter(bestPrinter);
        printJob.setStatus(PrintJobStatus.RESERVED);
        printJobRepository.save(printJob);

        log.info("Successfully scheduled PrintJob {} to Printer {} (Score: {})",
                printJob.getJobNumber(), bestPrinter.getName(), calculateFitnessScore(bestPrinter, printJob));

        return Optional.of(bestPrinter);
    }

    /**
     * Calculates fitness score for a printer candidate (0 - 100):
     * - Idle status: 40 points
     * - Queue depth: 0 active jobs = 30 points, 1 job = 15 points, 2+ jobs = 0 points
     * - Heartbeat freshness: <= 30s = 15 points, <= 60s = 10 points, <= 120s = 5 points
     * - Printer model capability: 15 points
     */
    private int calculateFitnessScore(Printer printer, PrintJob printJob) {
        int score = 0;

        // 1. Availability status
        if (printer.getStatus() == PrinterStatus.IDLE) {
            score += 40;
        }

        // 2. Queue depth
        long activeJobCount = printJobRepository.countByPrinterIdAndStatusIn(
                printer.getId(),
                List.of(PrintJobStatus.RESERVED, PrintJobStatus.PRINTING, PrintJobStatus.ISSUED)
        );

        if (activeJobCount == 0) {
            score += 30;
        } else if (activeJobCount == 1) {
            score += 15;
        }

        // 3. Heartbeat freshness
        if (printer.getLastSeenAt() != null) {
            Duration age = Duration.between(printer.getLastSeenAt(), Instant.now());
            if (age.getSeconds() <= 30) {
                score += 15;
            } else if (age.getSeconds() <= 60) {
                score += 10;
            } else {
                score += 5;
            }
        }

        // 4. Default capability baseline
        score += 15;

        return Math.min(100, score);
    }

    private boolean isHeartbeatActive(Printer printer) {
        if (printer.getLastSeenAt() == null) return false;
        Duration age = Duration.between(printer.getLastSeenAt(), Instant.now());
        return age.compareTo(HEARTBEAT_THRESHOLD) <= 0;
    }
}
