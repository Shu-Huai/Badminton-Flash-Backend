Set-Location C:\Badminton-Flash-Backend\
git pull
Get-ScheduledTask -TaskName 'Badminton-Flash-Backend' | Stop-ScheduledTask
mvn clean
mvn compile
mvn package
Get-ScheduledTask -TaskName 'Badminton-Flash-Backend' | Start-ScheduledTask
