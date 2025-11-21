# Running AILS-II on Compute Canada (Digital Research Alliance)

This guide explains how to run the AILS-II algorithm on Compute Canada clusters using SLURM job scheduling.

## Prerequisites

1. Active Compute Canada account
2. Access to a cluster (Graham, Cedar, Beluga, or Narval)
3. Basic knowledge of SSH and command line

## Quick Start

### 1. Transfer Files to Compute Canada

From your local machine:

```bash
# Replace USERNAME and CLUSTER with your credentials
rsync -avz --progress AILS-CVRP-main/ USERNAME@CLUSTER.computecanada.ca:~/AILS-CVRP-main/
```

Available clusters:
- `graham.computecanada.ca`
- `cedar.computecanada.ca`
- `beluga.computecanada.ca`
- `narval.computecanada.ca`

### 2. SSH to Compute Canada

```bash
ssh USERNAME@CLUSTER.computecanada.ca
cd ~/AILS-CVRP-main
```

### 3. Configure Your Account

**IMPORTANT**: Edit `scripts/run_slurm_array.sh` and replace the account line:

```bash
#SBATCH --account=def-youraccount  # REPLACE with your actual account
```

Find your account with: `sacctmgr show associations user=$USER`

### 4. Run on All Datasets

```bash
# This will automatically:
# 1. Build AILSII.jar
# 2. Generate instance list
# 3. Submit SLURM job array
bash scripts/run_dataset.sh Vrp_Set_A Vrp_Set_X Vrp_Set_XL_T
```

### 5. Run on Specific Dataset

```bash
# Run only on X instances
bash scripts/run_dataset.sh Vrp_Set_X

# Run only on XL_T instances
bash scripts/run_dataset.sh Vrp_Set_XL_T
```

## Monitoring Jobs

### Check Job Status

```bash
# View your jobs
squeue -u $USER

# Watch in real-time (updates every 10 seconds)
watch -n 10 'squeue -u $USER'

# Check specific job details
scontrol show job JOBID
```

### View Output Logs

```bash
# View SLURM output (real-time)
tail -f slurm_logs/ailsii_*.out

# View specific instance log
tail -f logs/Vrp_Set_X_X-n101-k25_job0.log

# List all output files
ls -lh slurm_logs/
ls -lh logs/
```

### Cancel Jobs

```bash
# Cancel specific job
scancel JOBID

# Cancel all your jobs
scancel -u $USER

# Cancel specific job array task
scancel JOBID_TASKID
```

## Customizing Parameters

### Adjust Time Limit per Instance

```bash
# Set 2 hours (7200 seconds) per instance
AILSII_TIME_LIMIT=7200 bash scripts/run_dataset.sh Vrp_Set_X
```

### Use Iteration Stopping Criterion

```bash
# Stop after 10000 iterations instead of time
AILSII_STOP_CRITERION=Iteration AILSII_TIME_LIMIT=10000 bash scripts/run_dataset.sh Vrp_Set_X
```

### Adjust SLURM Resources

Edit `scripts/run_slurm_array.sh`:

```bash
#SBATCH --mem=16G          # Increase memory to 16GB
#SBATCH --time=04:00:00    # Increase wall time to 4 hours
#SBATCH --cpus-per-task=2  # Use 2 CPU cores
```

### Available Environment Variables

- `AILSII_TIME_LIMIT` - Time limit in seconds or iteration count (default: 3600)
- `AILSII_STOP_CRITERION` - `Time` or `Iteration` (default: Time)
- `AILSII_ROUNDED` - `true` or `false` (default: true)

## File Structure

```
AILS-CVRP-main/
├── AILSII.jar              # Compiled JAR (auto-generated)
├── instance_list.txt       # Instance list (auto-generated)
├── data/                   # Dataset directories
│   ├── Vrp_Set_A/
│   ├── Vrp_Set_X/
│   └── Vrp_Set_XL_T/
├── scripts/
│   ├── build_jar.sh               # Compiles Java sources
│   ├── generate_instance_list.sh  # Creates instance list
│   ├── run_dataset.sh             # Main submission script
│   └── run_slurm_array.sh         # SLURM job script
├── slurm_logs/             # SLURM output/error logs
└── logs/                   # Algorithm output logs
```

## Manual Build (Optional)

If you need to rebuild the JAR manually:

```bash
bash scripts/build_jar.sh
```

## Testing Locally (Before SLURM Submission)

Test a single instance to verify everything works:

```bash
java -jar AILSII.jar \
    -file data/Vrp_Set_X/X-n101-k25.vrp \
    -rounded true \
    -best 27591 \
    -limit 60 \
    -stoppingCriterion Time
```

## Common Issues

### 1. Java Module Not Found

```bash
# Check available Java versions
module spider java

# Load specific version (use the one available on your cluster)
module load java/17.0.6

# Verify Java version
java -version
```

### 2. Account Not Specified

Error: `sbatch: error: Batch job submission failed: Invalid account`

**Solution**: Edit `scripts/run_slurm_array.sh` and set your account:
```bash
#SBATCH --account=def-professorname
```

### 3. Out of Memory

If jobs fail with memory errors, increase memory in `scripts/run_slurm_array.sh`:
```bash
#SBATCH --mem=16G  # or higher
```

### 4. Time Limit Exceeded

Increase the walltime:
```bash
#SBATCH --time=06:00:00  # 6 hours
```

Or reduce instances per job by editing the recommended jobs calculation in `generate_instance_list.sh`.

## Advanced Usage

### Run Custom Instance List

1. Create custom `instance_list.txt`:
```
X-n101-k25.vrp,Vrp_Set_X,27591
X-n106-k14.vrp,Vrp_Set_X,26362
```

2. Submit directly:
```bash
sbatch --array=0-9 scripts/run_slurm_array.sh
```

### Use Different Java Versions

```bash
# In scripts/run_slurm_array.sh, change:
module load java/17  # Use Java 17 instead of 11
```

### Parallel Parameter Sweep

Run multiple configurations in parallel:

```bash
# Terminal 1: Short runs
AILSII_TIME_LIMIT=1800 bash scripts/run_dataset.sh Vrp_Set_X

# Terminal 2: Long runs
AILSII_TIME_LIMIT=7200 bash scripts/run_dataset.sh Vrp_Set_X
```

## Getting Help

- Compute Canada documentation: https://docs.alliancecan.ca/
- SLURM documentation: https://slurm.schedmd.com/
- Check Java module versions: `module spider java`
- View your allocations: `sacctmgr show associations user=$USER`

## Performance Tips

1. **Use /scratch for I/O**: Large datasets should be in `/scratch` for better performance
2. **Adjust array size**: Balance between parallelism and scheduling overhead
3. **Monitor efficiency**: Use `seff JOBID` to check resource usage
4. **Email notifications**: Add to SLURM script:
   ```bash
   #SBATCH --mail-user=your.email@example.com
   #SBATCH --mail-type=END,FAIL
   ```

## Example Workflow

```bash
# 1. Login to Compute Canada
ssh user@graham.computecanada.ca

# 2. Navigate to project
cd ~/AILS-CVRP-main

# 3. Configure account (one-time)
nano scripts/run_slurm_array.sh  # Edit #SBATCH --account line

# 4. Run on all datasets with 2-hour time limit
AILSII_TIME_LIMIT=7200 bash scripts/run_dataset.sh Vrp_Set_A Vrp_Set_X Vrp_Set_XL_T

# 5. Monitor progress
watch -n 30 'squeue -u $USER'

# 6. Check results when complete
ls -lh slurm_logs/
tail logs/*.log
```

## Resources

- Default settings: 1 CPU, 8GB RAM, 2 hours walltime
- Adjust based on instance size
- Larger instances may need more memory and time
- Check cluster limits: https://docs.alliancecan.ca/wiki/Running_jobs
