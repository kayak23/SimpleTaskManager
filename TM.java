import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;
import java.time.Instant;

public class TM {

    public static void main(String[] args) {
        TextFileLog.getInstance();
        Command.execute(args);
    }
}

interface DataStore {
    void write(String line);
    String read();
}

enum TaskSize {
    S, M, L, XL;

    static boolean isValid(String arg) {
        try {TaskSize.valueOf(arg);}
        catch (Exception e) {
            System.out.println("Error: invalid size");
            return false;
        }
        return true;
    }

    static boolean isValidInParse(String arg) {
        try {TaskSize.valueOf(arg);}
        catch (Exception e) {return false;}
        return true;
    }

}

enum Command {
    start, stop, rename, describe, size, delete, summary;

    private static boolean isValid(String[] args) {
        try {Command.valueOf(args[0]);} 
        catch (Exception e) {
            System.out.println("Error: invalid command");
            return false;
        }
        return true;
    }

    public static void execute(String[] args) {
        if(!isValid(args)) {
            return;
        }
        Command command = Command.valueOf(args[0]);
        if(command == Command.start) {
            start(args);
        } else if(command == Command.stop) {
            stop(args);
        } else if(command == Command.rename) {
            rename(args);
        } else if(command == Command.describe) {
            describe(args);
        } else if(command == Command.size) {
            size(args);
        } else if(command == Command.delete) {
            delete(args);
        } else if(command == Command.summary) {
            summary(args);
        }
    }

    private static boolean doesTaskExist(String task) {
        if(TaskMap.getInstance().isTaskPresent(task)) {return true;}
        System.out.println("Error: Task DNE");
        return false;
    }

    private static boolean doEnoughTasksExist(String sizeString) {
        TaskSize size = TaskSize.valueOf(sizeString);
        if(TaskMap.getInstance().getCountOfTasksBySize(size)>1) {return true;}
        System.out.println("Error: too few tasks of size "+size);
        return false;
    }

    private static boolean isTaskNameValid(String name) {
        if(!TaskSize.isValidInParse(name)) {return true;}
        System.out.println("Error: invalid name; "+name+" is reserved token");
        return false;
    }

    private static void start(String[] args) {
        if(TaskMap.getInstance().isTaskActive(args[1])) {
            System.out.println("Error: Task already active");
            return;
        }
        if(!isTaskNameValid(args[1])) {return;}
        String logLine = args[0]+" "+args[1]+" "+TMTimer.getTime();
        TextFileLog.getInstance().write(logLine);
    }

    private static void stop(String[] args) {
        if(!TaskMap.getInstance().isTaskActive(args[1])) {
            System.out.println("Error: no such Task active");
            return;
        }
        String logLine = args[0]+" "+args[1]+" "+TMTimer.getTime();
        TextFileLog.getInstance().write(logLine);
    }

    private static void rename(String[] args) {
        if(!doesTaskExist(args[1])) {return;}
        if(!isTaskNameValid(args[1])) {return;}
        String logLine = args[0]+" "+args[1]+" "+args[2];
        TextFileLog.getInstance().write(logLine);
    }

    private static void describe(String[] args) {
        if(!doesTaskExist(args[1])) {return;}
        String logLine = "";
        for(String s : args) {logLine += s+" ";}
        logLine = logLine.substring(0, logLine.length()-1);
        TextFileLog.getInstance().write(logLine);
    }

    private static void size(String[] args) {
        if(!doesTaskExist(args[1])) {return;}
        if(!TaskSize.isValid(args[2])) {return;}
        String logLine = args[0]+" "+args[1]+" "+args[2];
        TextFileLog.getInstance().write(logLine);
    }

    private static void delete(String[] args) {
        if(!doesTaskExist(args[1])) {return;}
        String logLine = args[0]+" "+args[1];
        TextFileLog.getInstance().write(logLine);
    }

    private static void summary(String[] args) {
        if(args.length == 1) {
            TaskMap.getInstance().writeFullSummary(args);
        } else {
            if(TaskMap.getInstance().isTaskActive(args[1])) {
                TaskMap.getInstance().writeTaskSummary(args[1]);
            } else if(TaskSize.isValid(args[1])&&doEnoughTasksExist(args[1])) {
                TaskMap.getInstance().writeSizeSummary(args[1]);
            } else {return;}
        }
        TextFileLog.getInstance().write("wrote summary to output");
    }
}

class TMTimer {

    // Constants
    private static final String timestampFormat = "[dd/MM/yyyy-HH:mm:ss]";
    public static final int formatLength = timestampFormat.length();

    public static long getTime() {
        return Instant.now().getEpochSecond();
    }

    public static String getTimestamp() {
        SimpleDateFormat timestamp = new SimpleDateFormat(timestampFormat);
        return timestamp.format(1000*getTime());
    }

    public static String formatElapsedTime(long time) {
        long[] timeArray = secondsMinutesHours(time);
        String hourString = timeArray[2]+"h";
        String minuteString = timeArray[1]+"m";
        String secondString = timeArray[0]+"s";
        return hourString+minuteString+secondString;
    }

    public static long[] secondsMinutesHours(long totalSeconds) {
        long hours = totalSeconds/3600;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds - hours*3600)/60;
        long[] timeArray = {seconds, minutes, hours};
        return timeArray;
    }
}

class Task {

    // Fields
    public String description;
    public TaskSize size;
    public String name;

    private long startTime;
    private long endTime;
    private long totalTimeElapsed;
    private boolean isTaskActive;

    // Constants
    private static long nullTime = -1;

    public Task(String pName) {
        name = pName;
        totalTimeElapsed = 0;
        endTime = nullTime;
        isTaskActive = true;
    }

    public Task(String pName, long pStart) {
        name = pName;
        startTime = pStart;
        totalTimeElapsed = 0;
        endTime = nullTime;
        isTaskActive = true;
    }

    public void start(long time) {
        startTime = time;
        isTaskActive = true;
    }

    public void end(long time) {
        endTime = time;
        totalTimeElapsed += endTime-startTime;
        isTaskActive = false;
    }

    public boolean isActive() {
        return isTaskActive;
    }

    public long getTimeElapsed() {
        if(endTime < startTime) {
            return TMTimer.getTime()-startTime+totalTimeElapsed;
        }
        return totalTimeElapsed;
    }
}

// Lazy singleton
class TaskMap {

    // Fields
    private HashMap<String, Task> tasks;

    // Singleton
    private static TaskMap instance;

    private TaskMap() {
        tasks = new HashMap<String, Task>();
        initializeTasksFromDataStore();
    }

    public static TaskMap getInstance() {
        if(instance == null) {
            instance = new TaskMap();
        }
        return instance;
    }

    private void initializeTasksFromDataStore() {
        String line = TextFileLog.getInstance().read();
        while(line != null) {
            int start = TMTimer.formatLength+1;
            int end = line.length();
            String[] tokens = line.substring(start,end).split("\\s+");
            parseTokensToCommand(tokens);
            line = TextFileLog.getInstance().read();
        }
    }

    private void parseTokensToCommand(String[] tokens) {
        if(tokens[0].equals(Command.start.toString())) {
            parseStartCommand(tokens);
        } else if(tokens[0].equals(Command.stop.toString())) {
            parseStopCommand(tokens);
        } else if(tokens[0].equals(Command.rename.toString())) {
            parseRenameCommand(tokens);
        } else if(tokens[0].equals(Command.describe.toString())) {
            parseDescribeCommand(tokens);
        } else if(tokens[0].equals(Command.size.toString())) {
            parseSizeCommand(tokens);
        } else if(tokens[0].equals(Command.delete.toString())) {
            parseDeleteCommand(tokens);
        }
    }

    private void parseStartCommand(String[] args) {
        long time = Long.parseLong(args[2]);
        if(!tasks.containsKey(args[1])) {
            tasks.put(args[1], new Task(args[1],time));
        } else if(!tasks.get(args[1]).isActive()) {
            tasks.get(args[1]).start(time);
        }
    }

    private void parseStopCommand(String[] args) {
        long time = Long.parseLong(args[2]);
        tasks.get(args[1]).end(time);
    }

    private void parseRenameCommand(String[] args) {
        Task task = tasks.remove(args[1]);
        task.name = args[2];
        tasks.put(args[2], task);
    }

    private void parseDescribeCommand(String[] args) {
        int numArgs = args.length;
        String desc = "";
        for(int i=2;i<numArgs-1;i++) {
            desc += args[i]+" ";
        }
        if(TaskSize.isValidInParse(args[numArgs-1])) {
            tasks.get(args[1]).size = TaskSize.valueOf(args[numArgs-1]);
            desc = desc.substring(0, desc.length()-1);
        } else {
            desc += args[numArgs-1];
        }
        tasks.get(args[1]).description = desc;
    }

    private void parseSizeCommand(String[] args) {
        tasks.get(args[1]).size = TaskSize.valueOf(args[2]);
    }

    private void parseDeleteCommand(String[] args) {
        tasks.remove(args[1]);
    }

    public boolean isTaskActive(String key) {
        return isTaskPresent(key) && tasks.get(key).isActive();
    }

    public boolean isTaskPresent(String key) {
        return tasks.containsKey(key);
    }

    private long getMaxTimeElapsed(TaskSize size) {
        return tasks.values().stream()
                    .filter(task -> task.size == size)
                    .map(task -> task.getTimeElapsed())
                    .max(Long::compare).get();
    }

    private long getMinTimeElapsed(TaskSize size) {
        return tasks.values().stream()
                    .filter(task -> task.size == size)
                    .map(task -> task.getTimeElapsed())
                    .min(Long::compare).get();
    }

    private long getAvgTimeElapsed(TaskSize size) {
        return (long)(tasks.values().stream()
                    .filter(task -> task.size == size)
                    .map(task -> task.getTimeElapsed())
                    .collect(Collectors.averagingLong(task -> task))
                    .doubleValue()+0.5);
    }

    public long getCountOfTasksBySize(TaskSize size) {
        return tasks.values().stream()
                    .filter(task -> task.size == size)
                    .collect(Collectors.counting());
    }

    public void writeSizeSummary(String size) {
        TaskSize taskSize = TaskSize.valueOf(size);
        String min = TMTimer.formatElapsedTime(getMinTimeElapsed(taskSize));
        String max = TMTimer.formatElapsedTime(getMaxTimeElapsed(taskSize));
        String avg = TMTimer.formatElapsedTime(getAvgTimeElapsed(taskSize));
        System.out.println("Summary for tasks of size "+size+":");
        System.out.println("Minimum time spent: "+min);
        System.out.println("Maximum time spent: "+max);
        System.out.println("Average time spent: "+avg+"\n");
    }

    public void writeTaskSummary(String name) {
        String summary = "Time spent working on Task "+name;
        summary += " (Size "+tasks.get(name).size+"): ";
        summary += TMTimer.formatElapsedTime(tasks.get(name).getTimeElapsed());
        String description = "Description: "+tasks.get(name).description;
        System.out.println(summary+"\n"+description);
    }

    public void writeFullSummary(String[] args) {
        for(TaskSize size : TaskSize.values()) {
            if(getCountOfTasksBySize(size) > 1) {
                writeSizeSummary(size.toString());
            }
        }
    }
}

// Singleton class; lazy initialization
class TextFileLog implements DataStore {

    // Fields
    private File textFile;
    private PrintWriter fileWriter;
    private BufferedReader fileReader;

    // Singleton
    private static TextFileLog instance;

    // Constants
    private static final String fileName = "/activityLog.txt";

    private TextFileLog() {
        String path = System.getProperty("user.dir")+fileName;
        textFile = new File(path);
        initializeFileIO();
    }

    private void createTextFile() {
        try {Files.createFile(textFile.toPath());} 
        catch(Exception e) {/* File already exists */}
    }

    private void createFileReader() {
        try {
            FileReader reader = new FileReader(textFile);
            fileReader = new BufferedReader(reader);
        }
        catch(Exception e) {
            System.out.println("Error: cannot make fileReader");
        }
    }

    private void createFileWriter() {
        try {
            FileOutputStream fileStream = new FileOutputStream(textFile,true);
            fileWriter = new PrintWriter(fileStream,true);
        }
        catch(Exception e) {
            System.out.println("Error: cannot make fileWriter");
        }
    }

    private void initializeFileIO() {
        createTextFile();
        createFileWriter();
        createFileReader();
    }

    public static TextFileLog getInstance() {
        if(instance == null) {
            instance = new TextFileLog();
        }
        return instance;
    }

    public void write(String line) {
        try {fileWriter.println(TMTimer.getTimestamp()+" "+line);}
        catch(Exception e) {
            System.out.println("Error: Could not write to log");
        }
    }

    public String read() {
        try {return fileReader.readLine();}
        catch(Exception e) {
            System.out.println("Error: Could not read from log/EOF");
            return "";
        }
    }
}