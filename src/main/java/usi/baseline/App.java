package usi.baseline;

import java.io.*;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import slp.core.counting.giga.GigaCounter;
import slp.core.lexing.Lexer;
import slp.core.lexing.code.JavaLexer;
import slp.core.lexing.runners.LexerRunner;
import slp.core.modeling.Model;
import slp.core.modeling.dynamic.CacheModel;
import slp.core.modeling.dynamic.NestedModel;
import slp.core.modeling.mix.MixModel;
import slp.core.modeling.ngram.JMModel;
//import slp.core.modeling.runners.ModelRunner;
import slp.core.translating.Vocabulary;
import slp.core.util.Pair;


import slp.core.counting.Counter;
import slp.core.counting.io.CounterIO;

import slp.core.translating.VocabularyRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;


import java.util.Map;

import java.util.*;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

// to load the new version of modelrunner that includes Completion
import usi.baseline.ModelRunnerWithCompletion.Completion;

import java.sql.*;

import java.lang.*;

import java.sql.Connection;
import java.sql.DriverManager;

import java.nio.file.*;
import java.io.IOException;

import java.io.FileWriter;   // Import the FileWriter class

import java.io.File;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class App {

    // separator="/" for mac/unix, "\\" for windows
    public static String separator="/";

    // BASE_DIR is the directory where you will write train/test directories
    // EXPORT_DIR is the directory where you will export train/test files

    // app.java values
    //public static String BASE_DIR="/Users/ospite/IdeaProjects/baseline/dataset";
    //public static String EXPORT_DIR="/Users/ospite/IdeaProjects/baseline/export";

    // docker values
    public static String BASE_DIR="/tmp";
    public static String EXPORT_DIR="/export";

    public static Logger logger = Logger.getLogger("MyLog");
    public static FileHandler fh=null;
    // logger_path is the name (with path) for logger file
    // logger_path= "/results/logger.log if you run the docker, logger.log if you run the app.java
    public static String logger_path="/results/logger.log";
    // host is database if you run the docker, 127.0.0.1 if you run the app.java
    public static String host="database";
    // port is 3306 if you run the docker 63306 if you run app.java
    public static String port="3306";
    // writeabstract = False if you want to write original code, True if you want to write abstract code
    public static boolean writeabstract=false;
    // readfromfile=true if you want to read data from files (contained in EXPORT_DIR)
    // readfromfile=false if you want to read data from database
    public static boolean readfromfile=true;
    // writefiles = true if you want to export data contained in database (so that you can process method from text file instead of mysql)
    // writefiles = false if you don't want to export data (if you want to read data from MySQL or files has been already exported)
    public static boolean writefiles=false;


    static public class VerySimpleFormatter extends java.util.logging.Formatter {

        private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

        @Override
        public String format(final LogRecord record) {
            return String.format(
                    "%1$s %2$-7s %3$s\n",
                    new java.text.SimpleDateFormat(PATTERN).format(
                            new java.util.Date(record.getMillis())),
                    record.getLevel().getName(), formatMessage(record));
        }
    }

    // create a Repo class that contains the name and a list of methods associated to this repo
    static public class Repo {

        String name;
        ArrayList<Method> methods;

        Repo (String name_){

            name=name_;
            methods=new ArrayList();

        }

        void addMethod(Method m){ // add a method to repo

            methods.add(m);

        }

        void printRepo(){ // print the repo

            logger.info("___________________________________");
            logger.info("NAME: " + name);
            logger.info("NUM METHODS: " + methods.size());
            logger.info("___________________________________");

        }

        Method returnMethodById(int method_id){ // return the method with that specific method_id

            for (Method m :  methods){
                if (m.id==method_id){
                    return m;
                }
            }

            return null;
        }

        void printRepoComplete(){ // print the repo with more details

            logger.info("___________________________________");
            logger.info("REPO NAME: " + name);
            logger.info("NUM METHODS: " + methods.size());

            for (Method m: methods){
                logger.info("METHOD "+ m.id +": " + m.maskedmethods.size());
            }

            logger.info("___________________________________");
        }

        void writeRepo(String base_path, boolean writeabstract){ // write all the methods in the repo in the correct directory (respecting the hierarchy)

            String repo_name=this.name;

            String folder_repo=base_path+separator+repo_name.replace("/", separator);

            for (Method m : this.methods){ // for each method in repo

                String method_path=m.path;

                String[] parts = method_path.split("/");

                String filename=folder_repo;

                for (int i = 0; i<parts.length-1; i++){ // we skip filename (last part)

                    filename+=separator+parts[i];
                }

                String name= ""+m.id;

                createFolder(filename); // create the folder if not exists

                filename+=separator+name+".java";

                String code=m.code;

                if (writeabstract)
                    code=m.abstractcode;

                String[] lines=code.split(java.util.regex.Pattern.quote("\\n    ")); // split the file into lines

                writeFile(filename, lines, false); //write the file (false means on a single line)

            }

        }

    }

    // class Method contains all methods associated to a repo
    static public class Method {


        int id; // id of the method (method.id -> maskedmethod.method_id)
        int id_internal; // id_internal of the method
        String code;
        String abstractcode;
        String dataset;
        String repo;
        String path;

        ArrayList<MaskedMethod> maskedmethods;

        Method(int id_, String code_, String abstractcode_, String repo_, String path_, String dataset_, int id_internal_) //constructor
        {
            id=id_;
            code=code_;
            abstractcode=abstractcode_;
            repo=repo_;
            path=path_;
            dataset=dataset_;
            id_internal=id_internal_;

            maskedmethods=new ArrayList();
        }

        void printmethod(){ //print the method
            logger.info("___________________________________");
            logger.info("CODE: " + code);
            logger.info("ABSTRACT CODE: " + abstractcode);
            logger.info("REPO: " + repo);
            logger.info("PATH: " + path);
            logger.info("___________________________________");
        }


        void addMaskedMethod(MaskedMethod m){ // add the masked method

            maskedmethods.add(m);

        }


    }

    static public class MaskedMethod {

        int id; // id of maskedmethod
        int method_id; // method associated to maskedmethod (maskedmethod.method_id -> method.id)
        int id_internal;
        String masked_code;
        String mask;
        String abstract_masked_code;
        String abstract_mask;
        String dataset;
        int block_size;
        int block_nesting;
        int block_line;

        MaskedMethod(int id_, int method_id_, int id_internal_, String masked_code_, String mask_, String abstract_masked_code_,
                     String abstract_mask_, String dataset_, int block_size_, int block_nesting_, int block_line_){

            id=id_;
            method_id=method_id_;
            id_internal=id_internal_;
            masked_code=masked_code_;
            mask=mask_;
            abstract_masked_code=abstract_masked_code_;
            abstract_mask=abstract_mask_;
            dataset=dataset_;
            block_size=block_size_;
            block_nesting=block_nesting_;
            block_line=block_line_;
        }

    }

    // we create a class ModelClass to embed all parameters required for creating the Model
    public static class ModelClass {

        ModelRunnerWithCompletion modelRunner; // runner for the model
        Model model; // JM 6 nested and cached
        LexerRunner lexerRunner; // runner for the lexer
        Vocabulary vocabulary;
        File train;
        File test;

        ModelClass(LexerRunner lexerRunner_, File train_, File test_){ // we have to pass lexer and train/test dirs

            lexerRunner=lexerRunner_;
            train=train_;
            test=test_;

        }

        public void  TrainModel(){ //this is used to train the model


            vocabulary = new Vocabulary();

            GigaCounter gigacounter=new GigaCounter();

            model = new JMModel(6, gigacounter);

            model = new NestedModel(model, lexerRunner, vocabulary, test);
            model = MixModel.standard(model, new CacheModel());
            model.setDynamic(true);

            modelRunner = new ModelRunnerWithCompletion(model, lexerRunner, vocabulary);

            modelRunner.learnDirectory(train); // training of the Model on train directory

            // we save vocabulary and counter to be able to reload these files avoiding the retraining of the model

            String folder_model=BASE_DIR+separator+"Model";
            createFolder(folder_model);

            File vocab_file = new File(folder_model+separator+"vocabulary.vocab");
            File counter_file = new File(folder_model+separator+"counter.counts");

            VocabularyRunner.write(vocabulary, vocab_file);
            CounterIO.writeCounter(gigacounter, counter_file);

        }

        // we clone the model. We load vocabulary and counter and then we recreate the structure. This step does not require the training
        // I want to create a "freezed" model to use to predict every masked token with the same model
        // (the model learns something every prediction it makes and I don't want it)
        ModelClass cloneModel () {

            ModelClass m= new ModelClass (this.lexerRunner, this.train, this.test);

            String folder_model=BASE_DIR+separator+"Model";
            createFolder(folder_model);
            // load vocabulary and counter
            File vocab_file = new File(folder_model+separator+"vocabulary.vocab");

            Vocabulary vocabulary = VocabularyRunner.read(vocab_file);

            File counter_file = new File(folder_model+separator+"counter.counts");

            Counter counter=CounterIO.readCounter(counter_file);

            Model mm = new JMModel(6, counter);

            mm = new NestedModel(mm, this.lexerRunner, vocabulary, this.test);

            mm = MixModel.standard(mm, new CacheModel());

            mm.setDynamic(true);

            ModelRunnerWithCompletion modelRunner_ = new ModelRunnerWithCompletion(mm, this.lexerRunner, vocabulary);

            m.modelRunner=modelRunner_;
            m.model=mm;
            m.vocabulary=vocabulary;
            return m;

        }
    }

    public static boolean repoExists(ArrayList<Repo> repos, String n){ // check if repo already exists

        for (Repo r: repos){
            String nn=r.name;
            if (nn.equals(n)){
                return true;
            }
        }

        return false;

    }

    // check if a method with the same code exists in a specific repo
    // I can not use id field because it does not exist in exported files
    public static int methodExists(ArrayList<Repo> repos, String repo_name, String code){ // check if repo already exists

        for (Repo r: repos){
            String nn=r.name;
            if (nn.equals(repo_name)){

                for (Method m : r.methods) {
                    if (code.equals(m.code))
                        return m.id;
                }
            }
        }

        return -1; // return -1 if method does not exist

    }

    public static void addMethod(ArrayList<Repo> repos, String n, Method m){ // this method calls repo's method and can be used to add a method to a specific repo

        for (Repo r: repos){
            String nn=r.name;
            if (nn.equals(n)){
                r.addMethod(m);
                return;
            }
        }

    }

    public static void addMaskedMethod(ArrayList<Repo> repos, int method_id, MaskedMethod masked){ // this method calls method's method and can be used to add a masked method to a specific method

        for (Repo r: repos){
            for (Method m : r.methods){
                if (m.id ==method_id){
                    m.addMaskedMethod(masked);
                }
            }
        }
    }

    public static void printRepos(ArrayList<Repo> repos){ // print all repos

        for (Repo r: repos){
            r.printRepo();
        }
    }

    public static void printReposComplete(ArrayList<Repo> repos){ // print all repos (with more details)
        for (Repo r: repos){
            r.printRepoComplete();
        }
    }

    public static void deleteDirectoryRecursion(Path path) throws IOException { // delete all directories (including path). This function needs to be called inside try catch construct
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteDirectoryRecursion(entry);
                }
            }
        }
        Files.delete(path);
    }



    public static void createFolder(String path) { // create folder (with subfolders)

        File file = new File(path);
        //Creating the directory
        boolean bool = file.mkdirs();
    }

    // writeFile writes one or more lines of code in a specific position
    // we have 3 overloading of writeFile method ( we can pass an ArrayList, a String[] or a String)

    public static void writeFile(String path, ArrayList<String> lines, boolean multiline) {

        try {
            FileWriter myWriter = new FileWriter(path);
            if (multiline==true) {
                for (String line : lines)
                    myWriter.write(line+"\n");
            }
            else {
                String singleline="";
                for (String line : lines)
                    singleline+=line + " ";
                myWriter.write(singleline);

            }
            myWriter.close();
        } catch (IOException e) {
            logger.info("An error occurred.");
            e.printStackTrace();
        }
    }

    public static void writeFile(String path, String[] lines, boolean multiline) {

        try {
            FileWriter myWriter = new FileWriter(path);
            if (multiline==true) {
                for (String line : lines)
                    myWriter.write(line+"\n");
            }
            else {
                String singleline="";
                for (String line : lines)
                    singleline+=line + " ";
                myWriter.write(singleline);

            }
            myWriter.close();
        } catch (IOException e) {
            logger.info("An error occurred."+e.toString());
            e.printStackTrace();
        }
    }

    public static void writeFile(String path, String line) {

        try {
            FileWriter myWriter = new FileWriter(path);
            myWriter.write(line);

            myWriter.close();

        } catch (IOException e) {
            logger.info("An error occurred."+e.toString());
            e.printStackTrace();
        }
    }

    // this method returns the list of tokens contained in text string
    //ArrayList<String> result2=returnTokensFromText("private void main (String[] arr)", lexerRunner);
    public static ArrayList<String> returnTokensFromText(String text, LexerRunner lexerRunner){

        ArrayList<String> arr= new ArrayList();
        Stream<Stream<String>> AAA=lexerRunner.lexText(text);
        AAA.forEach(s -> s.forEach(g -> arr.add(g)));

        return arr;

    }

    // this method returns the list of tokens contained in filepath
    //ArrayList<String> result=returnTokens("C:\\Users\\Cini\\.spyder-py3\\STEP16\\Main\\test_new\\HelloWorld\\helloworld.java", lexerRunner);
    public static ArrayList<String> returnTokens(String filepath, LexerRunner lexerRunner){

        File ff= new File(filepath);
        ArrayList<String> arr= new ArrayList();
        Stream<Stream<String>> AAA=lexerRunner.lexFile(ff);
        AAA.forEach(s -> s.forEach(g -> arr.add(g)));

        return arr;

    }

    // main function to process a single test repo
    public static void processTest(Repo repo, String pathtest, ModelClass MOD, Map<String, Float> entropy, Map<String, Float> MRR){

        //remove all files from TEST PATH
        Path pp = Paths.get(pathtest);

        try {

            deleteDirectoryRecursion(pp);

        } catch (Exception ex) {
            logger.info("ERROR " +ex.toString());
        }

        createFolder(pathtest);

        //logger.info("FOLDER CREATED");

        // write all methods contained in the repo
        repo.writeRepo(pathtest, writeabstract);

        ArrayList<String> filepaths=new ArrayList();

        // if we want to predict a number of token > 1 we have to predict one token at a time.
        // We can predict the first token and then use the predicted token to get the next prediction or we can use the real token (ignoring the prediction)
        // If UsePrediction=false then we are using the real token, otherwise we're using the predicted one
        // If we use real token the performances improve
        // If the number of token to predict is equal to 1 this choice does not matter
        boolean UsePrediction=false;

        // add all files in filepaths
        try{
            Files.walk(Paths.get(pathtest))
                    .filter(Files::isRegularFile)
                    .forEach(g -> filepaths.add(g.toString()));
        }
        catch (IOException e) {
            logger.info("ERROR IN FILES WALK:" + e.toString());
        }

        // read the parameters of MOD (not trained yet)
        LexerRunner lexerRunner_= MOD.lexerRunner;
        File train_= MOD.train;
        File test_= MOD.test;

        // for each file
        for (String filepath : filepaths){

            logger.info("FILEPATH:" +filepath);

            String[] parts=filepath.split(java.util.regex.Pattern.quote(separator));

            String filename=parts[parts.length-1];

            logger.info("FILENAME:" +filename);

            // we retrieve the method_id from filename
            String method_id=filename.replace(".java", "");

            logger.info("METHOD_ID: "+method_id);
            // we load the method from repo
            Method m= repo.returnMethodById(Integer.parseInt(method_id));

            // number of masked methods associated to the method
            int num_masked=(m.maskedmethods).size();

            logger.info("NUM MASKED:" +num_masked);

            String original_code= writeabstract==false ?  m.code : m.abstractcode;

            // I realized that if I predict the same method twice in succession the probabilities of each token are slightly changed (as if it learned something from the current prediction)
            // I want to prevent the model from using information contained in the method that I'm predicting
            // My idea is to train model for each method, then freeze the model and reuse the freezed model to predict each masked code

            boolean ModelAlreadyTrained=false; // we want to train the model one time for each method => for the first maskedmethod we train the model, otherwise we load the trained model

            // we create all the variables used above
            ModelClass MOD1=new ModelClass(lexerRunner_, train_, test_);
            ModelClass MOD2=new ModelClass(lexerRunner_, train_, test_);
            ModelRunnerWithCompletion modelRunner=null;
            Vocabulary vocabulary=null;

            // for each MaskedMethod
            for (MaskedMethod mm : m.maskedmethods){

                String masked_code_original;
                String mask_original;
                if (writeabstract) {
                    masked_code_original = mm.abstract_masked_code;
                    mask_original= mm.abstract_mask;
                }
                else {
                    masked_code_original =  mm.masked_code;
                    mask_original= mm.mask;
                }


                String masked_code=masked_code_original.replace("<x>", "__MASK__");
                String mask= mask_original.replace("<z>", ""); // remove <z> contained in mask
                logger.info("MASKED AND MASK");
                logger.info(masked_code);
                logger.info(mask);

                // we use another lexer for the processing of the sentence (we want to prevent the model to learn something; however it should not happens )
                Lexer lexer = new JavaLexer();
                LexerRunner l = new LexerRunner(lexer, false);
                l.setSentenceMarkers(false);
                l.setExtension("java");

                // we create the list of tokens for mask and masked_code
                ArrayList<String> tokens_mask=returnTokensFromText(mask, l);

                ArrayList<String> tokens_masked_code=returnTokensFromText(masked_code.split("__MASK__")[0], l);

                //logger.info("MASKED CODE");
                //logger.info(masked_code.split("__MASK__")[0]);
                //logger.info("MASKED SIZE: "+tokens_masked_code.size());

                int size_masked=tokens_masked_code.size();
                int size_mask=tokens_mask.size();

                //empty file. we want to train the model on the dataset without the file we want to predict (to prevent him from "cheating" by using some information contained in this file)
                writeFile(filepath, "");

                File filetest= new File (filepath);

                // if we're processing the first masked method then the model has to be trained
                if (ModelAlreadyTrained == false){ // we train the model (first maskedmethod => the model is not trained yet)

                    MOD1.TrainModel(); // we use MOD1 as backup model
                    MOD2=MOD1.cloneModel(); // we use MOD2 to predict tokens
                    ModelAlreadyTrained=true;
                    modelRunner=MOD2.modelRunner;
                    vocabulary=MOD2.vocabulary;

                }
                else {
                    // load the trained model
                    MOD2=MOD1.cloneModel();
                    modelRunner=MOD2.modelRunner;
                    vocabulary=MOD2.vocabulary;

                }

                String masked_code_new= masked_code;

                logger.info("ADD FIRST TOKEN: "+ tokens_mask.get(0));

                masked_code_new=masked_code_new.replace("__MASK__", tokens_mask.get(0)+ " __MASK__"); // we add the real token

                String file_towrite=masked_code_new.replace("__MASK__", "");

                writeFile(filepath, file_towrite);

                logger.info("FILE TO WRITE");
                logger.info(file_towrite);

                List<List<Completion>> completefile = modelRunner.completeFile(filetest); // we ask the model to predict the current method

                String real_token=tokens_mask.get(0); // real token

                int count=0; // I use this variable to retrieve the prediction of the masked token (we know the position of the masked token)

                ArrayList<String> tokens_real= new ArrayList();

                for (List<Completion> completions_ : completefile){
                    logger.info("____________________________________");
                    logger.info("NUM OF TOKENS: " + completions_.size());

                    for (Completion completion : completions_){ // we have a list of Pair<token ID, Probability>

                        tokens_real.add(vocabulary.toWord(completion.getRealIx()));

                        logger.info("Real token id: "+completion.getRealIx()+ " Real Token Value: "+ vocabulary.toWord(completion.getRealIx()));

                        List<Pair<Integer, Double>> predictions=completion.getPredictions(); // list of predictions

                        logger.info("PREDICTIONS AND RANK");
                        logger.info(predictions.toString());

                        int rr=completion.getRank();

                        logger.info(Integer.toString(rr));

                        int rank=1; // I prefer to compute the rank instead of using the API
                        boolean found=false; // we set found = true when the prediction of masked token is between the top-10 predictions

                        double prob_first=-1; // I use this variable to check if I've already computed the entropy for that masked token

                        for (Pair<Integer, Double> p : predictions){

                            //logger.info("COUNT SIZE MASKED: "+count + " - "+size_masked);

                            if (count==(size_masked)){ // that is the prediction for masked token
                                logger.info("TOKEN MASKED PREDICTION");
                                if (prob_first<0){ // I get the best prediction to compute the entropy
                                    prob_first=p.right(); // prob_first now is > 0 => we can not enter in this if statement anymore (now prob_first is the best prediction probability)
                                    double INV_NEG_LOG_2 = -1.0/Math.log(2);
                                    double result=Math.log(prob_first) * INV_NEG_LOG_2;
                                    logger.info("ENTROPY: "+result);
                                    // I want to convert from double to float; casting does not work and I've found this workaround
                                    String ent_ = String.valueOf(result);
                                    float entr = Float.parseFloat(ent_);
                                    entropy.put(mm.method_id+"_"+mm.id, entr);

                                }
                            }

                            Integer word_int=p.left(); // id of word
                            Double prob= p.right(); // probability

                            String word=vocabulary.toWord(word_int);

                            if (count==size_masked)
                                logger.info("Token id: "+word_int+ " Token Value: "+word+" Prob: "+ p.right());

                            if (count==(size_masked) && real_token.equals(word)){ // the prediction of the model is correct (it may not be the first prediction)

                                //logger.info("RANK: "+rank); // this is the rank of the prediction
                                float MRR_curr=(float)1/rank; // MRR is the inverse of rank
                                MRR.put(mm.method_id+"_"+mm.id, MRR_curr);
                                logger.info("MRR: "+MRR_curr);
                                found=true;


                            }

                            rank++; // increase the rank variable

                        }

                        if (count==(size_masked) && found==false){ // if the token is not in top 10 prediction, we add 0 to MRR
                            MRR.put(mm.method_id+"_"+mm.id, (float)0);
                            logger.info("RANK NOT FOUND, MRR: "+0);
                        }

                        count++; // increase the count variable
                    }

                }


                String[] lines=original_code.split(java.util.regex.Pattern.quote("\\n    ")); // we write the real method in order to predict other methods

                writeFile(filepath, lines, false);

            }

        }

    }

    // read all methods and masked methods from mysql
    public static ArrayList<Repo> returnRepo() {

        ArrayList<Repo> repos= new ArrayList();

        //String host="database"; // 127.0.0.1 if you run app.java
        //String port="3306"; //63306 if you run app.java
        String dbName="usi";
        String username="user";
        String password="a74_o?221++K";
        String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?autoReconnect=true&useSSL=false";

        // "useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC"

        Connection conn = null;
        try {

            Class.forName("com.mysql.jdbc.Driver").newInstance(); //create an instance for mysql

            conn = DriverManager.getConnection(url, username, password);

            // query
            String query = "SELECT * FROM method";

            // create the java statement
            Statement st = conn.createStatement();

            // execute the query, and get a java resultset
            ResultSet rs = st.executeQuery(query);

            // iterate through the java resultset
            while (rs.next())
            {
                int id = rs.getInt("id");
                int id_internal = rs.getInt("id_internal");
                String code = rs.getString("code");
                String abstractcode = rs.getString("abstract_representation");
                String repo = rs.getString("repository");
                String path = rs.getString("path");
                String dataset=rs.getString("dataset");

                if (repoExists(repos, repo)==false){
                    Repo r = new Repo(repo);
                    repos.add(r);
                }



                Method meth=new Method(id, code, abstractcode, repo, path, dataset, id_internal);

                addMethod(repos, repo, meth); // add method to repo

            }
            st.close();


        } catch (Exception ex) {
            logger.info("ERROR " +ex);
        }

        try {

            Class.forName("com.mysql.jdbc.Driver").newInstance();

            conn = DriverManager.getConnection(url, username, password);


            String query = "SELECT * FROM masked_method"; // query to retrieve all masked methods


            // create the java statement
            Statement st = conn.createStatement();

            // execute the query, and get a java resultset
            ResultSet rs = st.executeQuery(query);

            // iterate through the java resultset
            while (rs.next())
            {
                int id = rs.getInt("id");
                int method_id = rs.getInt("method_id");
                int id_internal = rs.getInt("id_internal");
                String masked_code = rs.getString("masked_code");
                String mask = rs.getString("mask");
                String abstract_masked_code = rs.getString("abstract_masked_code");
                String abstract_mask = rs.getString("abstract_mask");
                String dataset=rs.getString("dataset");
                int block_size = rs.getInt("block_size");
                int block_nesting = rs.getInt("block_nesting");
                int block_line = rs.getInt("block_line");


                MaskedMethod masked=new MaskedMethod(id, method_id, id_internal, masked_code, mask, abstract_masked_code, abstract_mask, dataset, block_size, block_nesting, block_line);

                addMaskedMethod(repos, method_id, masked);

            }
            st.close();


        } catch (Exception ex) {
            logger.info("ERROR " +ex);
        }

        logger.info("NUMBER OF REPOS: "+repos.size());

        return repos;

    }

    // this function exports in EXPORT_DIR directory all files required to train and test the model
    // in this case we will use 90% train and 10% test
    // we use global variable writeabstract to write abstract or original code
    //the file are the following:
    // - repo/path for train
    // - repo/path for test
    // - original code for train (we train the model on the original method code)
    // - masked code for test (the method with <x> instead of the mask token
    // - mask code for test (the mask token with <z> at the end
    public static void writeTrainAndTest(String export_dir){
        ArrayList<Repo> repos=returnRepo(); // create repos that contains all repositories

        ArrayList<Repo> repos_train= new ArrayList<Repo>();
        ArrayList<Repo> repos_test = new ArrayList<Repo>();

        // 90 % train and 10% test
        int i=0;

        for (Repo r: repos){
            if (i % 10 ==0)
                repos_test.add(r);
            else
                repos_train.add(r);
            i++;
        }

        ArrayList<String> train_methods_list= new ArrayList();
        ArrayList<String> train_repo_list=new ArrayList();

        for (Repo repo : repos_train){

            for (Method m : repo.methods) {

                String result=m.code;

                if (writeabstract)
                    result=m.abstractcode;

                train_methods_list.add(result);
                train_repo_list.add(m.repo+separator+m.path);

            }


        }

        // write train files
        try {
            FileWriter writer = new FileWriter(export_dir+separator+"train_code.txt");
            for (String meth : train_methods_list){

                writer.write(meth+"\r\n");

            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            FileWriter writer = new FileWriter(export_dir+separator+"train_repo.txt");
            for (String rep : train_repo_list){

                writer.write(rep+"\r\n");

            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        ArrayList<String> test_masked_list= new ArrayList();
        ArrayList<String> test_mask_list=new ArrayList();
        ArrayList<String> test_repo_list=new ArrayList();


        for (Repo repo : repos_test){

            for (Method m : repo.methods) {

                for (MaskedMethod mm : m.maskedmethods) {

                    String result = mm.masked_code;

                    if (writeabstract)
                        result = mm.abstract_masked_code;

                    test_masked_list.add(result);

                    result = mm.mask;

                    if (writeabstract)
                        result = mm.abstract_mask;

                    test_mask_list.add(result);

                    test_repo_list.add(m.repo + separator + m.path);
                }
            }


        }

        // write test files
        try {
            FileWriter writer = new FileWriter(export_dir+separator+"test_mask.txt");
            for (String mask : test_mask_list){

                writer.write(mask+"\r\n");

            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            FileWriter writer = new FileWriter(export_dir+separator+"test_masked.txt");
            for (String masked : test_masked_list){

                writer.write(masked+"\r\n");

            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            FileWriter writer = new FileWriter(export_dir+separator+"test_repo.txt");
            for (String rep : test_repo_list){

                writer.write(rep+"\r\n");

            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }



    }
    // We read data from file to populate repo, method and maskedmethod class
    public static void PopulateRepoFromFiles (String exportdir, ArrayList<Repo> train, ArrayList<Repo> test){

        // read train repos
        String train_repo=exportdir+separator+"train_repo.txt";
        String train_code=exportdir+separator+"train_code.txt";
        String test_repo=exportdir+separator+"test_repo.txt";
        String test_mask=exportdir+separator+"test_mask.txt";
        String test_masked=exportdir+separator+"test_masked.txt";

        ArrayList<String> repo_train=new ArrayList<>();
        ArrayList<String> code_train=new ArrayList<>();
        ArrayList<String> repo_test=new ArrayList<>();
        ArrayList<String> mask_test=new ArrayList<>();
        ArrayList<String> masked_test=new ArrayList<>();


        try {

            Reader reader = new FileReader(train_repo);
            try(BufferedReader bufferedReader = new BufferedReader(reader)){

                String line = bufferedReader.readLine();
                repo_train.add(line);
                while(line != null) {
                    line = bufferedReader.readLine();
                    repo_train.add(line);
                }
            }
        } catch (IOException e) {

        }
        try {

            Reader reader = new FileReader(train_code);
            try(BufferedReader bufferedReader = new BufferedReader(reader)){

                String line = bufferedReader.readLine();
                code_train.add(line);
                while(line != null) {
                    line = bufferedReader.readLine();
                    code_train.add(line);
                }
            }
        } catch (IOException e) {

        }
        try {

            Reader reader = new FileReader(test_repo);
            try(BufferedReader bufferedReader = new BufferedReader(reader)){

                String line = bufferedReader.readLine();
                repo_test.add(line);
                while(line != null) {
                    line = bufferedReader.readLine();
                    repo_test.add(line);
                }
            }
        } catch (IOException e) {

        }
        try {

            Reader reader = new FileReader(test_mask);
            try(BufferedReader bufferedReader = new BufferedReader(reader)){

                String line = bufferedReader.readLine();
                mask_test.add(line);
                while(line != null) {
                    line = bufferedReader.readLine();
                    mask_test.add(line);
                }
            }
        } catch (IOException e) {

        }
        try {

            Reader reader = new FileReader(test_masked);
            try(BufferedReader bufferedReader = new BufferedReader(reader)){

                String line = bufferedReader.readLine();
                masked_test.add(line);
                while(line != null) {
                    line = bufferedReader.readLine();
                    masked_test.add(line);
                }
            }
        } catch (IOException e) {

        }

        logger.info(String.valueOf(repo_train.size()));
        logger.info(String.valueOf(repo_test.size()));
        logger.info(String.valueOf(code_train.size()));
        logger.info(String.valueOf(mask_test.size()));
        logger.info(String.valueOf(masked_test.size()));

        // fill train

        int len_train=repo_train.size();

        if (len_train != code_train.size())
        {
            logger.warning("TRAIN SIZE DOES NOT MATCH");
            return;
        }

        for (int i=0; i<len_train; i++) {

            String repo_curr = repo_train.get(i);
            String code_curr = code_train.get(i);

            //logger.info(repo_curr);
            if (repo_curr == null) {
                continue;
            }
            String[] parts = repo_curr.split("/");

            String repo = parts[0] + "/" + parts[1];

            String path = "";

            for (int j = 2; j < parts.length; j++)
                path += parts[j] + "/";

            path = path.substring(0, path.length() - 1);

            //logger.info("REPO: " + repo);
            //logger.info("PATH: " + path);

            // we add the repo if it is new
            if (repoExists(train, repo) == false) {
                Repo r = new Repo(repo);
                train.add(r);
            }

            Method meth = new Method(i, code_curr, code_curr, repo, path, "", -1);

            // we add the method if it is new
            if (methodExists(train, repo, code_curr)==-1) // method does not exist
            {
                addMethod(train, repo, meth); // add method to repo

            }

        }

        //printRepos(train);

        int len_test=repo_test.size();

        if (len_test != mask_test.size() || len_test != masked_test.size())
        {
            logger.warning("TEST SIZE DOES NOT MATCH");
            return;
        }


        for (int i=0; i<len_test; i++) {

            String repo_curr = repo_test.get(i);
            String mask_curr = mask_test.get(i);
            String masked_curr= masked_test.get(i);

            //logger.info(repo_curr);
            if (repo_curr == null) {
                continue;
            }
            String[] parts = repo_curr.split("/");

            String repo = parts[0] + "/" + parts[1];

            String path = "";

            for (int j = 2; j < parts.length; j++)
                path += parts[j] + "/";

            path = path.substring(0, path.length() - 1);

            //logger.info("REPO: " + repo);
            //logger.info("PATH: " + path);

            if (repoExists(test, repo) == false) {
                Repo r = new Repo(repo);
                test.add(r);
            }

            // code_curr is the original code of the method
            String code_curr=masked_curr.replace(" <x>", mask_curr.replace("<z>", ""));

            Method meth = new Method(i, code_curr, code_curr, repo, path, "", -1);

            int method_id=methodExists(test, repo, code_curr);

            if (method_id==-1) // add method if it is new
            {
                addMethod(test, repo, meth); // add method to repo
                method_id=i;

            }


            MaskedMethod masked=new MaskedMethod(i, method_id, -1, masked_curr, mask_curr, masked_curr, mask_curr, "", -1, -1, -1);

            addMaskedMethod(test, method_id, masked);

        }

    }

    // we read repos from database and we fill train and test repos
    public static void PopulateRepoFromDatabase(ArrayList<Repo> train, ArrayList<Repo> test){
        ArrayList<Repo> repos=returnRepo(); // create repos that contains all repositories

        // 90 % train and 10% test
        int i=0;

        for (Repo r: repos){
            if (i % 10 ==0)
                test.add(r);
            else
                train.add(r);
            i++;

        }
    }


    public static void main(String[] args) throws InterruptedException {

        // train and test repos
        String TRAIN_DIR=BASE_DIR+separator+"train";
        String TEST_DIR=BASE_DIR+separator+"test";

        createFolder(TRAIN_DIR);
        createFolder(TEST_DIR);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        logger.info("DATE START: "+dtf.format(now));

        try {
            fh = new FileHandler(logger_path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.addHandler(fh);
        //SimpleFormatter formatter = new SimpleFormatter();
        VerySimpleFormatter formatter = new VerySimpleFormatter();

        //System.setProperty("java.util.logging.SimpleFormatter.format",
        //        "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");

        fh.setFormatter(formatter);

        logger.info("START MAIN");

        if (writefiles) // we export mysql methods and masked methods
            writeTrainAndTest(EXPORT_DIR);

        ArrayList<Repo> repos_train= new ArrayList<Repo>();
        ArrayList<Repo> repos_test = new ArrayList<Repo>();

        if (readfromfile) // read data from files
            PopulateRepoFromFiles (EXPORT_DIR, repos_train, repos_test);
        else // read data from mysql
            PopulateRepoFromDatabase(repos_train, repos_test);


        boolean writeDirectories=true;

        if (writeDirectories){
            //this code will write all train directories
            for (Repo r : repos_train)
                r.writeRepo(TRAIN_DIR, writeabstract);

        }



        // 1. Lexing
        //   a. Set up lexer using a JavaLexer
        //		- The second parameter informs it that we want to files as a block, not line by line
        Lexer lexer = new JavaLexer();
        LexerRunner lexerRunner = new LexerRunner(lexer, false);
        //   b. Since our data does not contain sentence markers (for the start and end of each file), add these here
        //		- The model will assume that these markers are present and always skip the first token when modeling
        lexerRunner.setSentenceMarkers(false);
        //   c. Only lex (and model) files that end with "java". See also 'setRegex'
        lexerRunner.setExtension("java");

        ModelClass MOD = new ModelClass(lexerRunner, new File(TRAIN_DIR), new File(TEST_DIR));

        Map<String, Float> entropy = new HashMap<String, Float>();
        Map<String, Float> MRR = new HashMap<String, Float>();

        int numtestrepos=2; // number of test repos to predict
        for (int jj=0; jj<numtestrepos; jj++)
            processTest(repos_test.get(jj), TEST_DIR, MOD, entropy, MRR);

        logger.info("FINAL RESULTS");

        logger.info("ENTROPY");
        logger.info(entropy.toString());
        logger.info("MRR");
        logger.info(MRR.toString());

        // compute total and average entropy and MRR

        int num_keys_ent=(entropy.keySet()).size();

        float total_ent=0;
        for (String s : entropy.keySet()){
            total_ent+=entropy.get(s);
        }

        logger.info("Total Entropy: "+total_ent);
        logger.info("Average Entropy: "+total_ent/num_keys_ent);

        int num_keys_MRR=(MRR.keySet()).size();

        float total_MRR=0;
        for (String s : MRR.keySet()){
            total_MRR+=MRR.get(s);

        }

        logger.info("Total MRR: "+total_MRR);
        logger.info("Average MRR: "+total_MRR/num_keys_MRR);

        now = LocalDateTime.now();
        logger.info("DATE END: "+dtf.format(now));

    }
}