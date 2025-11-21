# Running AILS-II on Vrp_Set_XL_T for 24 Hours

## Configuration Summary

- **Dataset**: Vrp_Set_XL_T (100 instances)
- **Time Limit**: 24 hours per instance
- **Jobs**: 100 (1 instance per job)
- **Best Known Solution**: Disabled (set to 0)

## Quick Start

### 1. Transfer to Compute Canada

```bash
# From your local machine
rsync -avz --progress AILS-CVRP-main/ USERNAME@CLUSTER.computecanada.ca:~/AILS-CVRP-main/
```

### 2. SSH and Configure

```bash
ssh USERNAME@CLUSTER.computecanada.ca
cd ~/AILS-CVRP-main

# IMPORTANT: Edit scripts/run_slurm_array.sh
# Change line 10: #SBATCH --account=def-youraccount
# Replace 'youraccount' with your actual account
nano scripts/run_slurm_array.sh
```

### 3. Submit Jobs

```bash
# Run all 100 instances of XL_T dataset for 24 hours each, without best known solutions
bash scripts/run_dataset.sh --no-best-known Vrp_Set_XL_T
```

This will:
1. Build AILSII.jar (if needed)
2. Generate instance list with best=0 for all instances
3. Submit 100 SLURM jobs (array 0-99)
4. Each job runs 1 instance for 24 hours

### 4. Monitor Progress

```bash
# Check job status
squeue -u $USER

# Watch in real-time (updates every 30 seconds)
watch -n 30 'squeue -u $USER'

# View specific job output
tail -f slurm_logs/ailsii_JOBID_0.out

# Check how many jobs are running
squeue -u $USER | grep ailsii | wc -l
```

### 5. Check Results

```bash
# View logs for specific instances
ls -lh logs/
tail logs/Vrp_Set_XL_T_*.log

# Check SLURM outputs
ls -lh slurm_logs/
tail slurm_logs/ailsii_*.out
```

## Current Configuration

### SLURM Settings ([scripts/run_slurm_array.sh](scripts/run_slurm_array.sh))

```bash
#SBATCH --job-name=ailsii
#SBATCH --array=0-99              # 100 jobs (matches 100 instances)
#SBATCH --cpus-per-task=1         # 1 CPU per job
#SBATCH --mem=8G                  # 8GB RAM per job
#SBATCH --time=24:00:00          # 24 hour walltime
```

### AILS-II Parameters

- **Time Limit**: 86400 seconds (24 hours)
- **Stopping Criterion**: Time
- **Best Known**: 0 (disabled)
- **Rounded**: true

## Instance Distribution

With 100 instances and 100 jobs:
- **Instances per job**: 1
- **Total walltime**: 24 hours
- **Total compute hours**: 100 jobs × 24 hours = 2,400 core-hours

## Customization

### Change Time Limit

```bash
# Run for 12 hours instead of 24
AILSII_TIME_LIMIT=43200 bash scripts/run_dataset.sh --no-best-known Vrp_Set_XL_T
```

### Use Best Known Solutions

```bash
# Remove --no-best-known flag to use solutions from .sol files
bash scripts/run_dataset.sh Vrp_Set_XL_T
```

### Adjust Memory

If instances need more memory, edit `scripts/run_slurm_array.sh`:
```bash
#SBATCH --mem=16G  # Increase to 16GB
```

### Run Specific Instances

To test on a subset first:

```bash
# 1. Generate full instance list
bash scripts/generate_instance_list.sh --no-best-known Vrp_Set_XL_T

# 2. Keep only first 5 instances
head -5 instance_list.txt > instance_list_test.txt
mv instance_list_test.txt instance_list.txt

# 3. Submit with smaller array
sbatch --array=0-4 scripts/run_slurm_array.sh
```

## Monitoring Tips

### Job Efficiency

After jobs complete, check resource usage:
```bash
seff JOBID
```

This shows:
- CPU utilization
- Memory usage
- Walltime used

### Cancel Jobs

```bash
# Cancel all your jobs
scancel -u $USER

# Cancel specific job
scancel JOBID

# Cancel specific array task
scancel JOBID_5  # Cancel task 5
```

### Email Notifications

Add to `scripts/run_slurm_array.sh` (after #SBATCH directives):
```bash
#SBATCH --mail-user=your.email@example.com
#SBATCH --mail-type=END,FAIL
```

## Expected Output

Each instance will produce:
1. **SLURM log**: `slurm_logs/ailsii_JOBID_TASKID.out`
2. **Algorithm log**: `logs/Vrp_Set_XL_T_INSTANCENAME_jobTASKID.log`

Log files contain:
- Job start time
- Instance name and best known solution
- Algorithm progress
- Final solution
- Runtime statistics

## Troubleshooting

### Java Module Issues

Ensure Java 17 is loaded:
```bash
module load java/17.0.6
java -version  # Should show "openjdk version 17"
```

### Jobs Not Starting

Check your allocations:
```bash
sacctmgr show associations user=$USER
```

### Out of Memory

Increase memory in SLURM script:
```bash
#SBATCH --mem=16G  # or higher
```

### Jobs Timing Out

Some instances might not finish in 24 hours. You can:
1. Increase walltime: `#SBATCH --time=48:00:00`
2. Or save and resume (requires code modification)

### Check Available Resources

```bash
# Check cluster load
sinfo

# Check your job priority
sprio -u $USER
```

## Data Collection

After runs complete, you can analyze results:

```bash
# Extract best solutions from all logs
grep -h "best\|solution\|cost" logs/Vrp_Set_XL_T_*.log > results_summary.txt

# Count completed instances
ls logs/Vrp_Set_XL_T_*.log | wc -l
```

## Questions?

- Compute Canada docs: https://docs.alliancecan.ca/
- SLURM quick start: https://docs.alliancecan.ca/wiki/Running_jobs
- Check this project's main guide: [COMPUTE_CANADA_GUIDE.md](COMPUTE_CANADA_GUIDE.md)
