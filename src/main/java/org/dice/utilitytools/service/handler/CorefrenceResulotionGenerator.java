package org.dice.utilitytools.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.utils.Obj;
import com.google.common.collect.Ordering;
import edu.stanford.nlp.ling.CoreLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.text.StringEscapeUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import com.google.common.collect.Multimap;

import com.google.common.collect.TreeMultimap;

public class CorefrenceResulotionGenerator implements ITaskHandler<Void, HashMap<String, Boolean>> {
    private static final Logger LOGGGER = LoggerFactory.getLogger(CorefrenceResulotionGenerator.class);
    String destinationPath;

    public CorefrenceResulotionGenerator(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    @Override
    public Void handleTask(HashMap<String, Boolean> input) {
        CorefrenceResulotionGenerator CrR = new CorefrenceResulotionGenerator(null);
        try {
            for (Map.Entry<String, Boolean> entry : input.entrySet()) {
                String path = entry.getKey();
                if (path.endsWith(".json")) {
                    Boolean value = entry.getValue();
                    String textForCrR = Files.readString(Path.of(path));
                    String accutal = CrR.generateCrR(getJsonData(textForCrR));
                    System.out.println(accutal);
                    Files.write(Path.of(path.replace(".json", ".txt")), Collections.singleton(accutal));
                    System.out.println("File has been written successfully.");
                }
            }
        } catch (Exception ex) {
            LOGGGER.error(ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }

    private static String replaceQuotationMarks(String original) {
        if (original.startsWith("'") && original.endsWith("'")) {
            original = original.substring(1, original.length() - 1);
        } else if (original.startsWith("\"") && original.endsWith("\"")) {
            original = original.substring(1, original.length() - 1);
        }
        if (original.startsWith("``") && original.endsWith("''")) {
            original = original.substring(2, original.length() - 2);
        }
        return original;
    }

    public static String escapeQuotes(String input) {
        return input.replace("\"", "\\\"");
    }

    public static String fixErrors(String input) {
        return input.replaceAll("\\.(?!\\s)", ". ");
    }

    String getJsonData(String input) {
        String jsonText = "";

        input = fixErrors(input);
        try {
            // Initialize ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // Parse JSON data
            JsonNode jsonNode = objectMapper.readTree(input);

            for (JsonNode node : jsonNode) {
                jsonText += String.valueOf(node.get("maintext"));
                if (jsonNode.size() > 1) {
                    jsonText += "\n=========================================================================\n";
                }
            }
            // Print the formatted JSON text
            System.out.println(jsonText);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return jsonText;
    }

    List<String> startMultipleStringsCrR1(String[] input_strs) {
        List<String> sent_final = new ArrayList<>();
        // Input text
        List<String> text1 = new ArrayList<>();

//        int count = 0;
        for (String test : input_strs) {
            test = replaceQuotationMarks(test);
            text1.add(test);
        }
        Properties props = new Properties();
//        annotator "coref" requires annotation "CorefMentionsAnnotation". The usual requirements for this annotator are: tokenize,ssplit,pos,lemma,ner,mention
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,coref,dcoref");
        for (String text : text1) {
            Annotation document = new Annotation(text);
            StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
            pipeline.annotate(document);
            System.out.println("---");
            String para = "";
            Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
            for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
                System.out.println("orignal sentence---:" + sentence.toString() + "----");
                System.out.print("New sentence after co-ref: ");

                String modifiedSentence = "";
                if (corefChains != null) {
                    modifiedSentence = replaceMentions(sentence, corefChains, document, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
                    System.out.println(modifiedSentence);
                    para += " " + modifiedSentence;
                }
            }
            if (para.startsWith(" ")) {
                para = para.substring(1, para.length());
            }

            sent_final.add(para);
        }
        return sent_final;


    }

    private static String replaceMentions(CoreMap sentence, Map<Integer, CorefChain> corefChains, Annotation document, Integer sentence_num) {
        // Create a copy of the original sentence text
        String modifiedSentence = sentence.toString();
        int start_index = 0;
        int end_index = 0;
        HashMap<String, List<Object>> corefs = new HashMap<>();
        HashMap<String, List<Object>> start_coref = new HashMap<>();
        HashMap<String, List<Object>> end_coref = new HashMap<>();
        HashMap<String, List<Object>> mentionSpan = new HashMap<>();
        for (CorefChain corefChain : corefChains.values()) {

            if (((HashSet) (corefChain.getMentionMap().values()).toArray()[0]).size() > 1) {

                for (Object coref1 : ((HashSet) (corefChain.getMentionMap().values()).toArray()[0])) {
                    CorefChain.CorefMention representativeMention = corefChain.getRepresentativeMention();
                    if (coref1 == representativeMention) {
//                                    mentionSpan.add(representativeMention.mentionSpan);
                    } else {
                        if (((CorefChain.CorefMention) coref1).sentNum == sentence_num + 1) {
                            start_index = ((CorefChain.CorefMention) coref1).startIndex;
                            end_index = ((CorefChain.CorefMention) coref1).endIndex;
                            addIntegerToMap(mentionSpan, representativeMention.mentionSpan, representativeMention.mentionSpan);
                            addIntegerToMap(start_coref, representativeMention.mentionSpan, start_index);
                            addIntegerToMap(end_coref, representativeMention.mentionSpan, end_index);
                            addIntegerToMap(corefs, representativeMention.mentionSpan, coref1);
                        }
                    }
                }


            }

        }
        if (corefs.size() > 0) {
            modifiedSentence = replaceSubstring(corefs, start_coref, end_coref, mentionSpan, sentence);
        }
        return modifiedSentence;
    }

    private static String removeBrackets(String input) {
        if (input.startsWith("[") && input.endsWith("]")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private static void addIntegerToMap(Map<String, List<Object>> map, String key, Object value) {
        // If the key exists in the map, add the value to the existing list
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            // If the key doesn't exist, create a new list, add the value, and put it in the map
            List<Object> newList = new ArrayList<>();
            newList.add(value);
            map.put(key, newList);
        }
    }

    private static String replaceSubstring(HashMap<String, List<Object>> corefs, HashMap<String, List<Object>> startIndex, HashMap<String, List<Object>> endIndex, HashMap<String, List<Object>> rep, CoreMap document) {
        List<String> parts = new ArrayList<>();//original.split("(?=\\W)");
        int ii = 0;
        String replacemnt = "";

        for (CoreLabel label : document.get(CoreAnnotations.TokensAnnotation.class)) {
            parts.add(label.value().toString());
        }
        Multimap reps_start = sortByValueDescending(startIndex);
        Multimap reps_end = sortByValueDescending(endIndex);
        String final_res = "";
//        for (Object rep2:reps_start.values()) {

        // Create an iterator for each list
        Iterator<Object> iterator1 = reps_start.keySet().iterator();
        Iterator<Object> iterator2 = reps_end.keySet().iterator();
        // Iterate through both lists simultaneously using a foreach loop
        while (iterator1.hasNext() && iterator2.hasNext()) {
            Integer start = (Integer) iterator1.next();
            Integer end = (Integer) iterator2.next();
            for (int k = ii; k < start - 1; k++) {
                final_res += parts.get(k) + " ";
            }
            final_res += removeBrackets(reps_start.get(start).toString()) + " ";
            ii = end - 1;
        }

//        }
        if (ii < parts.size()) {
            for (int h = ii; h < parts.size(); h++) {
                final_res += parts.get(h) + " ";
            }
        }
        if (final_res.endsWith(" . ")) {
            final_res = final_res.substring(0, final_res.length() - 3) + ".";
        }
        if (final_res.startsWith(" ")) {
            final_res = final_res.substring(1, final_res.length());
        }
        return final_res;
    }

    private static Multimap<Integer, String> sortByValueDescending(Map<String, List<Object>> map) {

        // Create a TreeMultimap to store the reordered data
        Multimap<Integer, String> reorderedMultimap = TreeMultimap.create((o1, o2) -> Integer.compare(o1, o2), Ordering.natural());

        // Iterate through the original map and populate the multimap
        for (Map.Entry<String, List<Object>> entry : map.entrySet()) {
            String key = entry.getKey();
            List<Object> values = entry.getValue();

            for (Object value : values) {
                reorderedMultimap.put((Integer) value, key);
            }
        }

        // Print the reordered multimap
//        for (Map.Entry<Integer,String> entry : reorderedMultimap.entries()) {
//            System.out.println(entry.getKey() + ": " + entry.getValue());
//        }

        return reorderedMultimap;
    }

    String generateCrR(String input) {

        input = StringEscapeUtils.unescapeJava(input);
        if (input.contains("\n=========================================================================\n")) {
            String[] inputs = input.split("\n=========================================================================\n");
            List<String> resolvedText = startMultipleStringsCrR1(inputs);
            String ret = "";
            for (String res : resolvedText) {
                ret += res + "\n=========================================================================\n";
            }
            return ret;
        } else {
            // Create StanfordCoreNLP object with coreference resolution
            Properties props = new Properties();
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,dcoref");
            StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

            // Create an empty Annotation just with the given text
            Annotation document = new Annotation(input);

            // Run all annotators on the text
            pipeline.annotate(document);

            // Replace pronouns and noun phrases with their most typical mentions
            String resolvedText = replaceCoreferences(input, document);
            return resolvedText;
        }
    }

    public static String replaceCoreferences(String text, Annotation document) {
        String para = "";
        Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("orignal sentence---" + sentence.toString() + "----");
            System.out.println("MODIFIED SENTENCE after co-ref:");

            String modifiedSentence = "";
            if (corefChains != null) {
                modifiedSentence = replaceMentions(sentence, corefChains, document, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
                System.out.println(modifiedSentence);
                para += " " + modifiedSentence;
            }
        }
        if (para.startsWith(" ")) {
            para = para.substring(1, para.length());
        }
        text = para;
        return text;
    }
}
