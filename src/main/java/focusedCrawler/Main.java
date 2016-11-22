package focusedCrawler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import focusedCrawler.config.ConfigService;
import focusedCrawler.crawler.async.AsyncCrawler;
import focusedCrawler.crawler.async.AsyncCrawlerConfig;
import focusedCrawler.crawler.async.LocalDownloader;
import focusedCrawler.distributed.DistributedDownloader;
import focusedCrawler.distributed.FetcherNode;
import focusedCrawler.distributed.HazelcastService;
import focusedCrawler.link.LinkStorage;
import focusedCrawler.link.classifier.LinkClassifierFactoryException;
import focusedCrawler.link.frontier.FrontierManager;
import focusedCrawler.link.frontier.FrontierManagerFactory;
import focusedCrawler.link.frontier.FrontierPersistentException;
import focusedCrawler.seedfinder.SeedFinder;
import focusedCrawler.target.TargetStorage;
import focusedCrawler.target.classifier.WekaTargetClassifierBuilder;
import focusedCrawler.util.MetricsManager;

/**
 * <p>
 * Description: This is the main entry point for working with the components of
 * the focusedCrawler
 * </p>
 */
public class Main {
    
	public static final String VERSION = Main.class.getPackage().getImplementationVersion();
    
	public static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    private static Options[] allOptions;
    private static String[] commandName;

    public static void main(String... args) {
    	printVersion();
        try {
            CommandLineParser parser = new DefaultParser();
            
            Options startCrawlOptions = new Options();
            Options startCrawlerNodeOptions = new Options();
            Options startDistributedCrawlOptions = new Options();
            Options addSeedsOptions = new Options();
            Options buildModelOptions = new Options();
            
            startCrawlOptions.addOption("e", "elasticIndex", true, "ElasticSearch index name");
            startCrawlOptions.addOption("o", "outputDir", true, "Path to a folder to store crawler data");
            startCrawlOptions.addOption("c", "configDir", true, "Path to configuration files folder");
            startCrawlOptions.addOption("s", "seed", true, "Path to the file of seed URLs");
            startCrawlOptions.addOption("m", "modelDir", true, "Path to folder containing page classifier model");
            
            startCrawlerNodeOptions.addOption("o", "outputDir", true, "Path to a folder to store crawler data");
            startCrawlerNodeOptions.addOption("c", "configDir", true, "Path to configuration files folder");
            
            startDistributedCrawlOptions.addOption("e", "elasticIndex", true, "ElasticSearch index name");
            startDistributedCrawlOptions.addOption("o", "outputDir", true, "Path to a folder to store crawler data");
            startDistributedCrawlOptions.addOption("c", "configDir", true, "Path to configuration files folder");
            startDistributedCrawlOptions.addOption("s", "seed", true, "Path to the file of seed URLs");
            startDistributedCrawlOptions.addOption("m", "modelDir", true, "Path to folder containing page classifier model");
            
            addSeedsOptions.addOption("o", "outputDir", true, "Path to a folder to store crawler data");
            addSeedsOptions.addOption("c", "configDir", true, "Path to configuration files folder");
            addSeedsOptions.addOption("s", "seed", true, "Path to file of seed URLs");
            
            buildModelOptions.addOption("c", "stopWordsFile", true, "Path to stopwords file");
            buildModelOptions.addOption("t", "trainingDataDir", true, "Path to training data folder");
            buildModelOptions.addOption("o", "outputDir", true, "Path to folder which model built should be stored");
            buildModelOptions.addOption("l", "learner", true, "Machine-learning algorithm to be used to train the model (SMO, RandomForest)");
            
            allOptions = new Options[] { 
                    startCrawlOptions,
                    startCrawlerNodeOptions,
                    startDistributedCrawlOptions,
                    addSeedsOptions,
                    buildModelOptions};
            
            commandName = new String[] { 
                    "startCrawl",
                    "startCrawlerNode",
                    "startDistributedCrawl",
                    "addSeeds",
                    "buildModel",
                    "seedFinder"};

            if (args.length == 0) {
                printUsage();
                System.exit(1);
            }

            CommandLine cmd;
            if ("startCrawl".equals(args[0])) {
                cmd = parser.parse(startCrawlOptions, args);
                startCrawl(cmd);
            }
            else if("startCrawlerNode".equals(args[0])) {
                cmd = parser.parse(startCrawlerNodeOptions, args);
                startCrawlerNode(cmd);
            }
            else if("startDistributedCrawl".equals(args[0])) {
                cmd = parser.parse(startDistributedCrawlOptions, args);
                startDistributedCrawl(cmd);
            }
            else if ("addSeeds".equals(args[0])) {
                cmd = parser.parse(addSeedsOptions, args);
                addSeeds(cmd);
            }
            else if ("buildModel".equals(args[0])) {
                cmd = parser.parse(buildModelOptions, args);
                buildModel(cmd);
            }
            else if ("seedFinder".equals(args[0])) {
                SeedFinder.main(Arrays.copyOfRange(args, 1, args.length));
            }
            else if ("run".equals(args[0])) {
                runCliTool(args);
            }
            else {
                printUsage();
                System.exit(1);
            }
        }
        catch(MissingArgumentException e) {
            printMissingArgumentMessage(e);
            System.exit(1);
        }
        catch(ParseException e) {
            printError(e);
            System.exit(1);
        }
    }

    private static void runCliTool(String... args) {
        if(args.length <= 1) {
            System.out.println("ERROR: Class name of CLI tool not specified.");
            System.exit(1);
        }
        
        String toolClass = args[1];
        Class<?> loadedClass = null;
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                loadedClass = classLoader.loadClass("focusedCrawler.tools."+toolClass);
            } catch(ClassNotFoundException e) {
                // also try full class name
                loadedClass = classLoader.loadClass(toolClass);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Unable to find CLI tool named "+toolClass);
            System.exit(1);
        }
        
        // Execute main() method of loaded class
        String[] params = Arrays.copyOfRange(args, 2, args.length);
        try {
            Method mainMethod = loadedClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) params);
        } catch (Exception e) {
            System.out.printf("Failed to run tool %s.\n\n", loadedClass.getName());
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }

	private static void printVersion() {
		String header = "ACHE Crawler "+VERSION;
		for (int i = 0; i < header.length(); i++) {
			System.out.print("-");
		}
		System.out.println();
		System.out.println(header);
		for (int i = 0; i < header.length(); i++) {
			System.out.print("-");
		}
		System.out.println();
		System.out.println();
	}

    private static void printError(ParseException e) {
        System.out.println(e);
        System.out.println("Unable to parse the input. Did you enter the parameters correctly?\n");
        printUsage();
    }
    
    private static void printMissingArgumentMessage(MissingArgumentException e) {
        System.out.println("Unable to parse the input. "+e.getMessage()+"\n");
        printUsage();
    }

    private static void buildModel(CommandLine cmd) throws MissingArgumentException {
        
        String trainingPath  = getMandatoryOptionValue(cmd, "trainingDataDir");
        String outputPath    = getMandatoryOptionValue(cmd, "outputDir"); 
        String stopWordsFile = getOptionalOptionValue(cmd, "stopWordsFile");
        String learner       = getOptionalOptionValue(cmd, "learner");
        
        new File(outputPath).mkdirs();
        
        // generate the input for weka
        System.out.println("Preparing training data...");
        WekaTargetClassifierBuilder.createInputFile(stopWordsFile, trainingPath, trainingPath + "/weka.arff" );
        
        // generate the model
        System.out.println("Training model...");
        WekaTargetClassifierBuilder.trainModel(trainingPath, outputPath, learner);
        
        // generate features file
        System.out.println("Creating feature file...");
        WekaTargetClassifierBuilder.createFeaturesFile(outputPath,trainingPath);
        
        System.out.println("done.");
    }

    private static void addSeeds(CommandLine cmd) throws MissingArgumentException {
        String dataOutputPath = getMandatoryOptionValue(cmd, "outputDir");
        String configPath = getMandatoryOptionValue(cmd, "configDir");
        String seedPath = getMandatoryOptionValue(cmd, "seed");
        ConfigService config = new ConfigService(Paths.get(configPath, "ache.yml").toString());
        FrontierManager frontierManager = FrontierManagerFactory.create(config.getLinkStorageConfig(), configPath, dataOutputPath, seedPath, null);
        frontierManager.close();
    }

    private static void startCrawl(CommandLine cmd) throws MissingArgumentException {
        String seedPath = getMandatoryOptionValue(cmd, "seed");
        String configPath = getMandatoryOptionValue(cmd, "configDir");
        String modelPath = getMandatoryOptionValue(cmd, "modelDir");
        String dataOutputPath = getMandatoryOptionValue(cmd, "outputDir");
        String elasticIndexName = getOptionalOptionValue(cmd, "elasticIndex");
        
        ConfigService config = new ConfigService(Paths.get(configPath, "ache.yml").toString());
        
        try {
            MetricsManager metricsManager = new MetricsManager();
            
            LinkStorage linkStorage = LinkStorage.createLinkStorage(configPath, seedPath,
                    dataOutputPath, modelPath, config.getLinkStorageConfig(), metricsManager);

            // start target storage
            TargetStorage targetStorage = TargetStorage.createTargetStorage(
            		configPath, modelPath, dataOutputPath, elasticIndexName,
                    config.getTargetStorageConfig(), linkStorage);
            
            AsyncCrawlerConfig crawlerConfig = config.getCrawlerConfig();
            
            LocalDownloader downloader = new LocalDownloader(crawlerConfig, dataOutputPath,
                    targetStorage, linkStorage, metricsManager);
            
            // start crawl manager
            AsyncCrawler crawler = new AsyncCrawler(targetStorage, linkStorage, downloader);
            
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    crawler.shutdown();
                }
            });
            
            crawler.run();
            crawler.shutdown();
        }
        catch (LinkClassifierFactoryException | FrontierPersistentException  e) {
            logger.error("Problem while creating LinkStorage", e);
        }
        catch (IOException e) {
            logger.error("Problem while starting crawler.", e);
        }

    }
    
    private static void startCrawlerNode(CommandLine cmd) throws MissingArgumentException {
        String configPath = getMandatoryOptionValue(cmd, "configDir");
        String dataOutputPath = getMandatoryOptionValue(cmd, "outputDir");
        
        ConfigService config = new ConfigService(Paths.get(configPath, "ache.yml").toString());
        try {
            MetricsManager metricsManager = new MetricsManager();
            AsyncCrawlerConfig crawlerConfig = config.getCrawlerConfig();
            HazelcastService clusterService = new HazelcastService(config.getClusterConfig());
            
            FetcherNode fetcherNode = new FetcherNode(crawlerConfig, dataOutputPath,
                                                      metricsManager, clusterService);
            
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    fetcherNode.stop();
                    clusterService.stop();
                }
            });
            
            System.out.println("Starting fetcher node...");
            fetcherNode.start();
        }
        catch (Exception e) {
            logger.error("Problem while starting crawler node.", e);
        }

    }
    
    private static void startDistributedCrawl(CommandLine cmd) throws MissingArgumentException {
        String seedPath = getMandatoryOptionValue(cmd, "seed");
        String configPath = getMandatoryOptionValue(cmd, "configDir");
        String modelPath = getMandatoryOptionValue(cmd, "modelDir");
        String dataOutputPath = getMandatoryOptionValue(cmd, "outputDir");
        String elasticIndexName = getOptionalOptionValue(cmd, "elasticIndex");
        
        ConfigService config = new ConfigService(Paths.get(configPath, "ache.yml").toString());
        
        try {
            MetricsManager metricsManager = new MetricsManager();
            
            LinkStorage linkStorage = LinkStorage.createLinkStorage(configPath, seedPath,
                    dataOutputPath, modelPath, config.getLinkStorageConfig(), metricsManager);

            // start target storage
            TargetStorage targetStorage = TargetStorage.createTargetStorage(
                    configPath, modelPath, dataOutputPath, elasticIndexName,
                    config.getTargetStorageConfig(), linkStorage);

            HazelcastService clusterService = new HazelcastService(config.getClusterConfig());
            
            DistributedDownloader downloader = new DistributedDownloader(targetStorage, linkStorage,
                    clusterService, metricsManager);

            // start crawl manager
            AsyncCrawler crawler = new AsyncCrawler(targetStorage, linkStorage, downloader);
            
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    crawler.shutdown();
                    clusterService.stop();
                }
            });
            
            crawler.run();
            crawler.shutdown();
        }
        catch (LinkClassifierFactoryException | FrontierPersistentException  e) {
            logger.error("Problem while creating LinkStorage", e);
        }
        catch (IOException e) {
            logger.error("Problem while starting crawler.", e);
        }

    }

    private static String getMandatoryOptionValue(CommandLine cmd, final String optionName)
            throws MissingArgumentException {
        String optionValue = cmd.getOptionValue(optionName);
        if (optionValue == null) {
            throw new MissingArgumentException("Parameter "+optionName+" can not be empty.");
        }
        return optionValue;
    }
    
    private static String getOptionalOptionValue(CommandLine cmd, final String optionName){
        String optionValue = cmd.getOptionValue(optionName);
        return optionValue;
    }

    private static void printUsage() {

        HelpFormatter formatter = new HelpFormatter();
        for (int i = 0; i < allOptions.length; i++) {
            formatter.printHelp(commandName[i], allOptions[i], true);
            System.out.println();
        }

        System.out.println("Examples:\n");
        System.out.println("ache buildModel -c config/sample_config/stoplist.txt -t training_data -o output_model");
        System.out.println("ache addSeeds -o data -c config/sample_config -s config/sample.seeds");
        System.out.println("ache startCrawl -o data -c config/sample_config -s config/sample.seeds -m config/sample_model");
    }
}
