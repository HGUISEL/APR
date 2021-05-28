# ConPatFix (to be modified)
Revision of ConFix with patch recommendation data instead of change pool

Inspired by _**Automated Patch Generation with Context-based Change Application**_ <br>
- [Original Repository of ConFix](https://github.com/thwak/ConFix)


## FicCollect [bash script]

- Checkout defects4j data 
- Use git blame to get hash id of BFIC and FIC

### Output

- list FIC in csv format
path: /home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/FicCollect/{Project_name}_withFIC.csv
columns: [DefectsfJ ID,Faulty file path,faulty line,FIC]

- list BFIC in csv format
path: /home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/PatchSuggestion/output/{Project_name}_withBFIC.csv
columns: [DefectsfJ ID,Faulty file path,faulty line,FIC,BFIC,project,dummy,lable]

## PatchSuggestion [bash script]

- Use change-vector-colletor: Collect change vectors between BFICs and FICs.
- Use SimFin: Use change collected change vectors to get suggested patches.
    
### Output

- change-vector-collector output
path: 
/home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/PatchSuggestion/output/testset/X_test.csv
/home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/PatchSuggestion/output/testset/Y_test.csv
columns: 
X_test.csv: list of change vectors
Y_test.csv: [index, path_BBIC, path_BIC, sha_BBIC, sha_BIC, path_BBFC, path_BFC, sha_BBFC, sha_BFC, key, project, label]
but, [path_BBFC, path_BFC, sha_BBFC, sha_BFC, key] is replaced with {Project_name}-{ID}


SimFin results in csv format
- SHA of BFICs
- File path in BFICs
- SHA of suggested patches
- File path of suggested patches
- Project names
path: /home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/PatchSuggestion/output/eval/test_result.csv
columns: [ Y_BIC_SHA, Y_BIC_Path, Y_Project, Y_BugId, Y^_Project, dummy, Rank, Sim-Score, BI_lines,Label, Project, Y^_BIC_SHA, Y^_BIC_Path, Y^_BIC_Hunk, Y^_BFC_SHA, Y^_BFC_Path, Y^_BFC_Hunk ]
but, the columns are modified from original SimFin result format.
also, Y^_BFC_Hunk is replaced with '_' ro make the csv simple. it can be restored.

## Run LAS(Location Aware Source code differencing) [bash script + python]

[Link to LAS](https://github.com/thwak/LAS)

### prepare_LAS_input.sh

1. Checkout all necessary projects with SHA from commit db in defects4j.
2. Copy all the buggy and clean files into `code_dir` directory.
3. Checkout all necessary projects of suggested patch.
4. Copy all the buggy and clean files into `code_dir` directory.
5. Put all the file path into `LAS_input.csv`.
path: /home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/LAS_input.csv
columns: [DFJ ID, neighbor rank, orig_BIC path, orig_BFC path, rec_BIC path, rec_BFC path]

### runLAS.py

1. Read LAS_input.csv.
2. Run LAS twice for each from original patch and suggested patch.
3. Put all the change info into `LAS_output.csv`
path: /home/DPMiner/APR_Projects/ConPatFix/TEYH_pool/LAS_output.csv
columns: [DFJ ID, Rank, orig change info, suggested change info]