param([string]$TrustStoreType = '')

# Run tests and always generate a report, but preserve the failing test exit code.
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17+ installation first.' }
Push-Location $PSScriptRoot
try {
    $testArgs = @('-B', 'clean', 'test')
    if ($TrustStoreType) { $testArgs += "-Dssl.truststore.type=$TrustStoreType" }
    & .\mvnw.cmd @testArgs
    $testExit = $LASTEXITCODE
    if (!(Test-Path 'target/allure-results/*-result.json')) {
        throw 'No test results: inspect the build or setup failure above.'
    }
    & .\mvnw.cmd -B allure:report
    if ($LASTEXITCODE -ne 0) { throw 'Allure report generation failed.' }
    & .\.allure\allure-2.29.0\bin\allure.bat generate target/allure-results --clean --single-file -o report
    if ($LASTEXITCODE -ne 0) { throw 'Single-file report generation failed.' }
    Write-Host "Report: $PSScriptRoot/report/index.html; test exit code: $testExit"
} finally {
    Pop-Location
}
exit $testExit