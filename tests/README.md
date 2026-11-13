# Testes

Para rodar os testes básicos, na pasta do projeto:

```bash
mvn test
```

Os testes de integração precisam de PostgreSQL e ficam desativados por padrão. Eles apagam e recriam o schema `public` antes de cada teste. Por isso, use somente um banco de teste vazio chamado `foodlink_test`, sem dados que precise guardar.

Crie esse banco no pgAdmin e coloque como proprietário o mesmo usuário configurado em `db.properties`. Esse usuário precisa ter permissão para recriar o schema.

No PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/foodlink_test"
$env:FOODLINK_TESTES_BANCO = "1"
mvn test
Remove-Item Env:DB_URL, Env:FOODLINK_TESTES_BANCO
```

No Linux ou macOS:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/foodlink_test'
export FOODLINK_TESTES_BANCO=1
mvn test
unset DB_URL FOODLINK_TESTES_BANCO
```

Ajuste o host e a porta se forem diferentes. O usuário e a senha são lidos do `db.properties` local, a menos que `DB_USER` e `DB_PASSWORD` também estejam definidos como variáveis de ambiente.
