Set-Location C:\Where-Money\Where-Money-Backend\
git pull
Get-ScheduledTask -TaskName 'Badminton-Flash-Backend' | Stop-ScheduledTask
mvn clean
mvn compile
mvn package
Get-ScheduledTask -TaskName 'Badminton-Flash-Backend' | Start-ScheduledTask
