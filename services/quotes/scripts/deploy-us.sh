# deploy JIT to Cloud Run
export PROJECT_ID=$(gcloud config list --format 'value(core.project)')
echo   "Project id: $PROJECT_ID"

echo "Deploy: 1Gi and 1 CPU - default deployment "
gcloud run deploy quotes \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 1Gi --cpu=1 \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=true \
     --no-cpu-boost \
     --allow-unauthenticated

echo "Deploy: 1Gi and 1 CPU"
gcloud run deploy quotes-1-1 \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 1Gi --cpu=1 \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=false \
     --no-cpu-boost \
     --allow-unauthenticated

echo "Deploy: 2Gi and 1 CPU"
gcloud run deploy quotes-2-1 \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 2Gi --cpu=1 \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=false \
     --no-cpu-boost \
     --allow-unauthenticated

echo "Deploy: 2Gi and 2 CPU"
gcloud run deploy quotes-2-2 \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 2Gi --cpu=2 \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=false \
     --no-cpu-boost \
     --allow-unauthenticated     

echo "Deploy: 2Gi and 2 CPU and JAVA TOOLS"
gcloud run deploy quotes-2-2-tools \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 2Gi --cpu=2 \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=false \
     --set-env-vars=JAVA_TOOL_OPTIONS='-XX:+UseG1GC -XX:MaxRAMPercentage=80 -XX:ActiveProcessorCount=2 -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k' \
     --no-cpu-boost \
     --allow-unauthenticated        

echo "Deploy: 2Gi and 1 CPU and CPU Boost"
gcloud run deploy quotes-2-1-boost \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 2Gi --cpu=1 \
     --cpu-boost \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=false \
     --allow-unauthenticated          

echo "Deploy: 4Gi and 4 CPU"
gcloud run deploy quotes-4-4 \
     --image us-central1-docker.pkg.dev/${PROJECT_ID}/quotes/quotes \
     --region us-central1 \
     --memory 4Gi --cpu=4 \
     --execution-environment gen1 \
     --set-env-vars=SPRING_FLYWAY_ENABLED=false \
     --no-cpu-boost \
     --allow-unauthenticated     
