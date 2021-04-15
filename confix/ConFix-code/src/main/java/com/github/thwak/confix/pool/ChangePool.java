package com.github.thwak.confix.pool;

import com.github.thwak.confix.util.IOUtils;
import com.github.thwak.confix.util.IndexMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ChangePool {

    private static final String CONTEXT_FILE_NAME = "context.obj";
    private static final String IDENTIFIER_FILE_NAME = "identifier.obj";
    private static final String CHANGE_DIR_NAME = "changes";
    private static final String HASH_ID_FILE_NAME = "hash_id_map.obj";
    public String poolName;
    private File poolDir;
    public ContextInfo contextInfo;
    public IndexMap<Change> changes;
    public IndexMap<String> hashIdMap;
    public int maxLoadCount = 1000;
    private ContextIdentifier identifier;
    public ContextInfo info = new ContextInfo();

    public ChangePool() {
        this(new File("pool"), new ContextIdentifier());
    }

    public ChangePool(File poolDir) {
        this(poolDir, new ContextIdentifier());
    }

    public ChangePool(ContextIdentifier identifier) {
        this(new File("pool"), identifier);
    }

    public ChangePool(File poolDir, ContextIdentifier identifier) {
        this.poolDir = poolDir;
        this.identifier = identifier;
        contextInfo = new ContextInfo();
        changes = new IndexMap<>();
        hashIdMap = new IndexMap<>();

        if (!this.poolDir.exists()) {
            try {
                this.poolDir.mkdir(); // 폴더 생성합니다.
            } catch (Exception e) {
                e.getStackTrace();
            }
        }
    }

    public void clearAll() {
        contextInfo = new ContextInfo();
        changes.clear();
        hashIdMap.clear();
    }

    public void clear() {
        changes.clear();
    }

    public void add(Change change) {
        add(change, 1);
    }

    public void add(Change change, int freq) {
        if (!hashIdMap.contains(change.hash)) {
            store(change);
        }
        int changeId = hashIdMap.add(change.hash);
        if (contextInfo == null) {
            contextInfo = new ContextInfo();
        }
        contextInfo.addChange(changeId, change, freq);

        // System.out.println("This is current context: "+context);
        // System.out.println("This is the size of Contexts: "+contexts.size());
    }

    private void store(Change c) {
        File changeDir = new File(poolDir.getAbsolutePath() + File.separator + CHANGE_DIR_NAME);
        if (!changeDir.exists()) {
            try {
                changeDir.mkdir(); // 폴더 생성합니다.
            } catch (Exception e) {
                e.getStackTrace();
            }
        }
        File fChange = new File(changeDir + File.separator + c.hash + ".obj");
        IOUtils.storeObject(fChange, c);
    }

    public List<Integer> getChangeIds() {
        if (contextInfo != null) {
            return new ArrayList<>(contextInfo.getChanges());
        }
        return Collections.emptyList();
    }

    public Iterator<Integer> changeIterator() {
        if (contextInfo != null) {
            return contextInfo.getChanges().iterator();
        }
        return Collections.emptyIterator();
    }

    public int getFrequency(Change change) {
        int id = hashIdMap.getIndex(change.hash);
        return contextInfo != null ? contextInfo.getChangeFreq(id) : 0;
    }

    public void loadFrom(File poolDir) {
        if (poolDir.isFile()) {
            System.out.println("Can't load from file - " + poolDir.getAbsolutePath());
            System.out.println("You must provide a pool directory.");
            return;
        } else if (poolDir.exists()) {
            //Load contexts only.
            this.poolDir = poolDir;
            String path = poolDir.getAbsolutePath();
            File contextFile = new File(path + File.separator + CONTEXT_FILE_NAME);
            File hashIdFile = new File(path + File.separator + HASH_ID_FILE_NAME);
            if (hashIdFile.exists()) {
                hashIdMap = (IndexMap<String>) IOUtils.readObject(hashIdFile);
            }
            File identifierFile = new File(path + File.separator + IDENTIFIER_FILE_NAME);
            if (identifierFile.exists()) {
                identifier = (ContextIdentifier) IOUtils.readObject(identifierFile);
            }
        }
    }

    public void storeTo(File poolDir, boolean saveAll) {
        if (poolDir.isFile()) {
            System.out.println("Can't store to file - " + poolDir.getAbsolutePath());
            System.out.println("You must provide a pool directory.");
            return;
        }
        if (poolDir.exists() || poolDir.mkdirs()) {
            this.poolDir = poolDir;
            String path = poolDir.getAbsolutePath();
            File contextFile = new File(path + File.separator + CONTEXT_FILE_NAME);
            File hashIdFile = new File(path + File.separator + HASH_ID_FILE_NAME);
            IOUtils.storeObject(hashIdFile, hashIdMap);
            File identifierFile = new File(path + File.separator + IDENTIFIER_FILE_NAME);
            IOUtils.storeObject(identifierFile, identifier);
            if (saveAll) {
                String changeDirPath = path + File.separator + CHANGE_DIR_NAME;
                File changeDir = new File(changeDirPath);
                if (!changeDir.exists()) {
                    changeDir.mkdir();
                }

                System.out.println("=========== Start to save hash object set ==========");


                for (Integer id : changes.indexSet()) {
                    Change c = changes.get(id);
                    File fChange = new File(changeDir + File.separator + c.hash + ".obj");
                    System.out.println(changeDir + File.separator + c.hash + ".obj");
                    IOUtils.storeObject(fChange, c);
                }
            }
        }
    }


    public ContextIdentifier getIdentifier() {
        return identifier;
    }

    public Change getChange(int id) {
        loadChange(id);
        return changes.get(id);
    }

    public void loadChange(int id) {
        if (!changes.hasIndex(id) && hashIdMap.hasIndex(id)) {
            if (changes.size() >= maxLoadCount) {
                changes.clear();
            }
            //#TODO: Store/Load changes with one big RandomAcessFile.
            String changeDirPath = poolDir.getAbsolutePath() + File.separator + CHANGE_DIR_NAME;
            File changeDir = new File(changeDirPath);
            File fChange = new File(changeDir + File.separator + hashIdMap.get(id) + ".obj");
            Change c = (Change) IOUtils.readObject(fChange);
            changes.put(id, c);
        }
    }
}