package com.anysoftkeyboard.dictionaries;

import com.anysoftkeyboard.nextword.*;


import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import androidx.core.util.Pair;
import com.anysoftkeyboard.AnySoftKeyboardRobolectricTestRunner;
import com.anysoftkeyboard.api.KeyCodes;
import com.menny.android.anysoftkeyboard.AnyApplication;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;


class MetricCollector {
    // KS = 100% * ( 1 - ( 1 / |W| ) * Sum( num_of_used_keys / len_of_word ) )
    // WPR = 100% * ( |W_predicted_correctly| / |W| )
    public long numOfWords = 0;
    private long numOfWordsPredicted = 0;
    private double currentSumResult = 0;

    public void updateSum(long numOfKeysUsed, long wordsLen) {
        currentSumResult += (double)numOfKeysUsed / (double)wordsLen;
        numOfWords++;
    }

    public double getKeystrokeSavings() {
        return 100 * (1 - currentSumResult / numOfWords);
    }

    public void incrementNumOfPredictedWords() {
        numOfWordsPredicted++;
    }

    public double getWordPredictionRate() {
        return 100 * ((double)numOfWordsPredicted / (double)numOfWords);
    }
}

class TestWordsReader {
    private List<String> wordsList;
    private long wordsCount; // 45.291.291 words in test dataset,  695.472 uniques of them
    private static final Pattern mWordLineRegex =
            Pattern.compile("([\\w\\p{L}'\"-]+)");
    public void readFile (String path) throws IOException {
        List<String> wl = new ArrayList<String>();
        wordsCount = 0;
        try {
            List<String> wordsListTmp = Files.readAllLines(Paths.get(path))
                    .stream()
                    .map(l -> l.split(" "))
                    .flatMap(Arrays::stream)
                    .collect(Collectors.toList());
            for (String word : wordsListTmp) {
                word = word.replaceAll("[!\"\\#$%&()*+,./:;=?@\\[\\\\\\]^_`{|}~]", "");
                Matcher matcher = mWordLineRegex.matcher(word);
                if (matcher.matches()) {
                    wl.add(word);
                    wordsCount++;
                }
            }
            wordsList = wl;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getWordsList(int nElem) {
        Collections.shuffle(wordsList);
        return wordsList.subList(0,nElem);
    }

    public List<String> getWordsList() {
        return wordsList;
    }

    public void getUniqueWords(String outputFileName) throws IOException {
        HashSet<String> uniqueValues = new HashSet<>(wordsList);
        BufferedWriter out = new BufferedWriter(new FileWriter(outputFileName));

        Iterator<String> it = uniqueValues.iterator();
        while(it.hasNext()) {
            out.write(it.next());
            out.newLine();
        }
        out.close();

//            System.out.println(uniqueValues.size());
    }
}

class DictionaryWordsReader {
    //    private File inputFile =
//            new File("..\\..\\addons\\languages\\english\\pack\\dictionary\\aosp.combined.gz");
    private File inputFile =
            new File("..\\..\\addons\\languages\\english\\pack\\build\\dictionary\\words_merged.xml");
    private int maxWordsInList = 500000;
    private long countWordsRead;

    public List<Pair<String, Integer>> generateWordsList() throws IOException {
        if (inputFile == null) {
            throw new IllegalArgumentException("Please provide inputFile value.");
        }
        if (!inputFile.isFile()) throw new IllegalArgumentException("inputFile must be a file!");

        final long inputSize = inputFile.length();
        System.out.println(
                "Reading input file " + inputFile.getName() + " (size " + inputSize + ")...");

        InputStream fileInput = new FileInputStream(inputFile);
        //     <w f="1">Reserve</w>
        Pattern mWordLineRegex = Pattern.compile("^\\s*<w f=\"(\\d+)\">([&;\\w\\p{L}'\"-]+)</w>.*$");
        int freqGroupNum = 1;
        int wordGroupNum = 2;
        if (inputFile.getName().endsWith(".zip")) {
            fileInput = new ZipInputStream(fileInput);
            freqGroupNum = 2;
            wordGroupNum = 1;
        } else if (inputFile.getName().endsWith(".gz")) {
            fileInput = new GZIPInputStream(fileInput);
            // word=heh,f=0,flags=,originalFreq=53,possibly_offensive=true
            mWordLineRegex = Pattern.compile("^\\s*word=([\\w\\p{L}'\"-]+),f=(\\d+).*$");
            freqGroupNum = 2;
            wordGroupNum = 1;
        }

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(fileInput, StandardCharsets.UTF_8));
        String wordDataLine;

        List<Pair<String, Integer>> dictionaryWordsList = new ArrayList<Pair<String, Integer>>();

        try {
            long read = 0;
            long wordsWritten = 0;
            while (null != (wordDataLine = reader.readLine())) {
                read += wordDataLine.length();
                Matcher matcher = mWordLineRegex.matcher(wordDataLine);
                if (matcher.matches()) {
                    String word = matcher.group(wordGroupNum);
                    word = escapeXml(word);
                    int frequency = Integer.parseInt(matcher.group(freqGroupNum));
                    dictionaryWordsList.add(new Pair<String, Integer>(word, frequency));
                    if ((wordsWritten % 50000) == 0) {
                        System.out.print("." + ((100 * read) / inputSize) + "%.");
                    }
                    wordsWritten++;
                    countWordsRead = wordsWritten;
                    if (maxWordsInList == wordsWritten) {
                        System.out.println("!!!!");
                        System.out.println(
                                "Reached " + maxWordsInList + " words! Breaking parsing.");
                        break;
                    }
                }
            }
            System.out.print(".100%.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Done.");
        return dictionaryWordsList;
    }

    // from XmlW
    private static String escapeXml(String str) {
        str = replaceString(str, "&amp;","&");
        str = replaceString(str, "&lt;", "<");
        str = replaceString(str, "&gt;", ">");
        str = replaceString(str, "&quot;", "\"");
        str = replaceString(str, "&apos;", "'");
        return str;
    }

    // from StringW
    private static String replaceString(String text, String repl, String with) {
        return replaceString(text, repl, with, -1);
    }

    /**
     * Replace a string with another string inside a larger string, for the first n values of the
     * search string.
     *
     * @param text String to do search and replace in
     * @param repl String to search for
     * @param with String to replace with
     * @param max int values to replace
     * @return String with n values replacEd
     */
    private static String replaceString(String text, String repl, String with, int max) {
        if (text == null) {
            return null;
        }

        StringBuffer buffer = new StringBuffer(text.length());
        int start = 0;
        int end = 0;
        while ((end = text.indexOf(repl, start)) != -1) {
            buffer.append(text.substring(start, end)).append(with);
            start = end + repl.length();

            if (--max == 0) {
                break;
            }
        }
        buffer.append(text.substring(start));

        return buffer.toString();
    }
}

@RunWith(AnySoftKeyboardRobolectricTestRunner.class)
public class QualityMetricsTest {

    private SuggestionsProvider mProvider;
    private Suggest mUnderTest;
    private ExternalDictionaryFactory mFactory;
    private NextWordDictionary mNextWordDictionaryUnderTest;

    private static void typeWord(WordComposer wordComposer, String word) {
        final boolean[] noSpace = new boolean[word.length()];
        Arrays.fill(noSpace, false);
        typeWord(wordComposer, word, noSpace);
    }

    private static void typeWord(WordComposer wordComposer, String word, boolean[] nextToSpace) {
        for (int charIndex = 0; charIndex < word.length(); charIndex++) {
            final char c = word.charAt(charIndex);
            wordComposer.add(c, nextToSpace[charIndex] ? new int[] {c, KeyCodes.SPACE} : new int[] {c});
        }
    }

    private void comparePrevAndNextWords(CharSequence prevWord, CharSequence nextWord, AtomicBoolean isPredictedEqualWithNextWord) {
        mNextWordDictionaryUnderTest.getNextWords(prevWord.toString(), 3, 0)
                .forEach(x -> isPredictedEqualWithNextWord.set(isPredictedEqualWithNextWord.get() | Objects.equals(
                        nextWord.toString(), x.toLowerCase())));
    }

    private int getNextWordPredictionInfluence(int i, List<String> wordsList, AtomicBoolean isPredictedEqualWithNextWord, MetricCollector metricMajor, MetricCollector metricMinor, boolean sideWordsChoosingFlag) {
        mNextWordDictionaryUnderTest.notifyNextTypedWord(wordsList.get(i).toString());
        if (i >= wordsList.size() - 1) return wordsList.size() - 1;
        comparePrevAndNextWords(wordsList.get(i), wordsList.get(i + 1), isPredictedEqualWithNextWord);

        while (isPredictedEqualWithNextWord.get() && i < wordsList.size() - 2) {
            metricMajor.incrementNumOfPredictedWords();
            metricMajor.updateSum(1, 1 + wordsList.get(i + 1).length());
            if (sideWordsChoosingFlag && metricMinor != null) {
                metricMinor.incrementNumOfPredictedWords();
                metricMinor.updateSum(1, 1 + wordsList.get(i + 1).length());
            }
            i++;
            isPredictedEqualWithNextWord.set(false);
            comparePrevAndNextWords(wordsList.get(i), wordsList.get(i + 1), isPredictedEqualWithNextWord);
        }
        return i;
    }

    @Before
    public void setUp() throws Exception {
        mFactory = AnyApplication.getExternalDictionaryFactory(getApplicationContext());
        mProvider = Mockito.mock(SuggestionsProvider.class);
        mUnderTest = new SuggestImpl(mProvider);
        mNextWordDictionaryUnderTest = new NextWordDictionary(getApplicationContext(), "en");
    }

    @Test
    public void testGetMetrics() throws IOException {
        // Get current time
        long start = System.currentTimeMillis();
        // Read test dataset to list of words
        TestWordsReader twr = new TestWordsReader();
        List<String> wordsList = Collections.emptyList();
        try {
//            twr.readFile("..\\..\\..\\WordPrediction\\unked-clean-dict-15k\\unked-clean-dict-15k\\en-sents-shuf.00.test.txt");
//            wordsList = twr.getWordsList(50000);
            twr.readFile("..\\..\\..\\WordPrediction\\eval_kss_en.txt");
            wordsList = twr.getWordsList();

//            twr.getUniqueWords("..\\..\\..\\WordPrediction\\uniques-in-eval-kss.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Prepare dictionary for further prediction
        // TODO: change from naive version to stock
        mUnderTest.setCorrectionMode(true, 1, 2, true);
        DictionaryWordsReader dwr = new DictionaryWordsReader();
        List<Pair<String, Integer>> dictionaryWordsList = dwr.generateWordsList();
        Mockito.doAnswer(
                        invocation -> {
                            final Dictionary.WordCallback callback = invocation.getArgument(1);
                            for (Pair<String, Integer> wordToAdd : dictionaryWordsList) {
                                callback.addWord(wordToAdd.first.toCharArray(), 0, wordToAdd.first.length(), wordToAdd.second, Mockito.mock(Dictionary.class));
                            }
                            return null;
                        })
                .when(mProvider)
                .getSuggestions(Mockito.any(), Mockito.any());

        // Make a key wise typing emulation to get a suggestion list from a dictionary
        MetricCollector metricMajor = new MetricCollector(); // for first word in a list of suggestions
        MetricCollector metricMinor = new MetricCollector(); // for first 3 words

        mNextWordDictionaryUnderTest.load();
        for (int i = 0; i < wordsList.size(); i++) {
            CharSequence word = wordsList.get(i);
            WordComposer wordComposer = new WordComposer();
            int j = 0;
            boolean sideWordsChoosingFlag = true; // to divide major and minor metrics count
            while (j < word.length()) {
                typeWord(wordComposer, "" + word.charAt(j));
                j++;

                List<CharSequence> suggestions = mUnderTest.getSuggestions(wordComposer);

                AtomicBoolean isPredictedEqualWithNextWord = new AtomicBoolean(false);

                // suggestions.get(0) - typed word, suggestions.get(k) - suggestions
                if (Objects.equals(word.toString(), suggestions.get(0).toString().toLowerCase())) {
                    metricMajor.updateSum(j, word.length());
                    if (sideWordsChoosingFlag) metricMinor.updateSum(j, word.length());

                    // Next word prediction learning and metrics influence
                    i = getNextWordPredictionInfluence(
                            i, wordsList, isPredictedEqualWithNextWord, metricMajor, metricMinor, sideWordsChoosingFlag);

                    break;
                }
                if (Objects.equals(word.toString(), suggestions.get(1).toString().toLowerCase())) {
                    metricMajor.updateSum(j, word.length());
                    if (sideWordsChoosingFlag) metricMinor.updateSum(j, word.length());

                    // Next word prediction learning and metrics influence
                    i = getNextWordPredictionInfluence(
                            i, wordsList, isPredictedEqualWithNextWord, metricMajor, metricMinor, sideWordsChoosingFlag);

                    break;
                }
                try {
                    if (Objects.equals(word.toString(), suggestions.get(2).toString().toLowerCase()) && sideWordsChoosingFlag) {
                        metricMinor.updateSum(j, word.length());
                        sideWordsChoosingFlag = false;
                        // Next word prediction learning and metrics influence
                        getNextWordPredictionInfluence(
                                i, wordsList, isPredictedEqualWithNextWord, metricMinor, null, false);
                    }
                    if (Objects.equals(word.toString(), suggestions.get(3).toString().toLowerCase()) && sideWordsChoosingFlag) {
                        metricMinor.updateSum(j, word.length());
                        sideWordsChoosingFlag = false;
                        // Next word prediction learning and metrics influence
                        getNextWordPredictionInfluence(
                                i, wordsList, isPredictedEqualWithNextWord, metricMinor, null, false);
                    }
                } catch (IndexOutOfBoundsException e) {}


            }

            if (i % 100 == 0) {
                System.out.println("." + ((100 * i) / wordsList.size()) + "%.");
                System.out.println("KS metric for TOP-1 word suggestion: " + metricMajor.getKeystrokeSavings() + "%. Num of predicted words: " + metricMajor.numOfWords);
                System.out.println("KS metric for TOP-3 words suggestion: " + metricMinor.getKeystrokeSavings() + "%. Num of predicted words: " + metricMinor.numOfWords);

                System.out.println("WPR metric for TOP-1 word suggestion: " + metricMajor.getWordPredictionRate() + "%. Num of predicted words: " + metricMajor.numOfWords);
                System.out.println("WPR metric for TOP-3 words suggestion: " + metricMinor.getWordPredictionRate() + "%. Num of predicted words: " + metricMinor.numOfWords);

                long elapsedTimeMillis = System.currentTimeMillis()-start;
                System.out.println("Elapsed time: " + elapsedTimeMillis/1000F + "s");
            }
        }
        NextWordStatistics nextWordStatistics = mNextWordDictionaryUnderTest.dumpDictionaryStatistics();
        mNextWordDictionaryUnderTest.close();


        long elapsedTimeMillis = System.currentTimeMillis()-start;

        System.out.println("\nFinal KS metric for TOP-1 word suggestion: " + metricMajor.getKeystrokeSavings() + "%. Num of predicted words: " + metricMajor.numOfWords);
        System.out.println("Final KS metric for TOP-3 words suggestion: " + metricMinor.getKeystrokeSavings() + "%. Num of predicted words: " + metricMinor.numOfWords);

        System.out.println("\nFinal WPR metric for TOP-1 word suggestion: " + metricMajor.getWordPredictionRate() + "%. Num of predicted words: " + metricMajor.numOfWords);
        System.out.println("Final WPR metric for TOP-3 words suggestion: " + metricMinor.getWordPredictionRate() + "%. Num of predicted words: " + metricMinor.numOfWords);

        System.out.println("Next word dictionary statistic:\n\tFirst word: "+ nextWordStatistics.firstWordCount + ";\n\tSecond words: " + nextWordStatistics.secondWordCount + ".");
        // Get elapsed time in seconds
        System.out.println("Elapsed time: " + elapsedTimeMillis/1000F + "s");
    }
}
