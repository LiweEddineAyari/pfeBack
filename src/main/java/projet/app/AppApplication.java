package projet.app;

import org.apache.poi.util.IOUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Banking ETL DataMart Application
 * Phase 1: Extraction
 * 
 * This application handles:
 * - Excel file detection and validation
 * - Raw data extraction to staging tables
 * - ETL logging and monitoring
 */
@SpringBootApplication
@EnableScheduling
public class AppApplication {

	public static void main(String[] args) {
		// Increase POI byte array limit for large Excel files (200MB)
		IOUtils.setByteArrayMaxOverride(200_000_000);
		
		SpringApplication.run(AppApplication.class, args);
		System.out.println("========================================");
		System.out.println("Banking ETL Extraction Service Started");
		System.out.println("========================================");
	}

}

