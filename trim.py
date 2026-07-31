with open('app/src/main/java/com/example/ohrana/PdfReportGenerator.kt', 'r') as f:
    content = f.read()

brace = 0
end = 0
for i, c in enumerate(content):
    if c == '{':
        brace += 1
    elif c == '}':
        brace -= 1
        if brace == 0:
            end = i
            break

with open('app/src/main/java/com/example/ohrana/PdfReportGenerator.kt', 'w') as f:
    f.write(content[:end+1])

print(f"Cut at index {end}")
