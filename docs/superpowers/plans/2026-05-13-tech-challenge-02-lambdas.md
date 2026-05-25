# Plan 3: lambdas — Monorepo Go (login + authorizer + email) com Terraform separado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o repositório `auto-repair-shop-lambdas` (monorepo Go) com 3 Lambdas — `login`, `authorizer`, `email` — código `internal/` compartilhado, e 2 sub-projetos Terraform (`terraform/auth/` e `terraform/email/`) com state separado. Pipelines com path-filter pra deploy por sub-projeto.

**Architecture:** Monorepo Go com `cmd/<lambda>/main.go` para entrypoints e `internal/<package>` para código compartilhado. Cada Lambda compila como binário `bootstrap` (custom runtime `provided.al2023`, ARM64). Auth sub-projeto cria 2 funções (login, authorizer) × 2 envs + HTTP API Gateway + VPC Link + Authorizer config. Email sub-projeto cria 2 funções × 2 envs + 2 SNS topics + 2 SQS queues + DLQs.

**Tech Stack:** Go 1.23, `aws-lambda-go`, `aws-sdk-go-v2`, `golang-jwt/jwt/v5`, `golang.org/x/crypto/bcrypt`, `lib/pq` para PostgreSQL, `slog` para logs JSON, `otel` para tracing. Terraform AWS provider.

---

## Pré-requisitos

- **Plans 1 e 2 completados**
- Go 1.23+ instalado
- `gh`, `aws`, `terraform` disponíveis
- Spec aprovada

---

## File Structure

```
auto-repair-shop-lambdas/
├── .github/workflows/
│   ├── pr-check.yaml
│   └── deploy.yaml
├── .gitignore
├── README.md
├── go.mod
├── go.sum
├── Makefile
├── cmd/
│   ├── login/main.go
│   ├── authorizer/main.go
│   └── email/main.go
├── internal/
│   ├── secrets/manager.go + manager_test.go
│   ├── observability/logger.go + tracer.go
│   ├── jwt/signer.go + verifier.go + jwt_test.go
│   ├── cpf/validate.go + validate_test.go
│   ├── password/bcrypt.go + bcrypt_test.go
│   ├── user/repository.go + repository_test.go
│   ├── mailersend/client.go + client_test.go
│   ├── template/quote.go
│   └── model/event.go
├── tests/
│   └── e2e/  (smoke tests opcionais)
└── terraform/
    ├── auth/
    │   ├── backend.tf, providers.tf, variables.tf, data.tf,
    │   ├── lambda.tf, apigw.tf, secrets.tf, outputs.tf, ssm.tf
    └── email/
        ├── backend.tf, providers.tf, variables.tf, data.tf,
        ├── sns.tf, sqs.tf, subscription.tf, lambda.tf,
        ├── eventsource.tf, secrets.tf, outputs.tf, ssm.tf
```

---

## Task 1: Bootstrap do repositório

**Files:**
- Create: GitHub repo, `.gitignore`, `go.mod`, `Makefile`

- [ ] **Step 1: Criar repo e estrutura base**

```bash
gh repo create ivanzao/auto-repair-shop-lambdas \
  --private \
  --description "Go monorepo: login + authorizer + email Lambdas with separate Terraform states" \
  --clone
cd auto-repair-shop-lambdas

mkdir -p cmd/{login,authorizer,email} \
         internal/{secrets,observability,jwt,cpf,password,user,mailersend,template,model} \
         terraform/{auth,email} \
         .github/workflows
```

- [ ] **Step 2: `.gitignore`**

```bash
cat > .gitignore <<'EOF'
*.zip
bootstrap
dist/
.terraform/
*.tfstate
*.tfstate.*
*.tfvars
*.tfvars.json
coverage.out
.idea/
*.swp
EOF
```

- [ ] **Step 3: `go.mod` + dependências base**

```bash
go mod init github.com/ivanzao/auto-repair-shop-lambdas
go get github.com/aws/aws-lambda-go/lambda
go get github.com/aws/aws-lambda-go/events
go get github.com/aws/aws-sdk-go-v2/config
go get github.com/aws/aws-sdk-go-v2/service/secretsmanager
go get github.com/golang-jwt/jwt/v5
go get golang.org/x/crypto/bcrypt
go get github.com/lib/pq
go get go.opentelemetry.io/otel
go get go.opentelemetry.io/otel/sdk
go get go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc
go get go.opentelemetry.io/contrib/instrumentation/github.com/aws/aws-lambda-go/otellambda
```

- [ ] **Step 4: `Makefile`**

```bash
cat > Makefile <<'EOF'
.PHONY: test lint build-login build-authorizer build-email build-all clean

GO := go
GOOS := linux
GOARCH := arm64

test:
	$(GO) test ./... -race -cover

lint:
	golangci-lint run ./...

build-%:
	@mkdir -p dist/$*
	GOOS=$(GOOS) GOARCH=$(GOARCH) CGO_ENABLED=0 \
		$(GO) build -tags lambda.norpc -o dist/$*/bootstrap ./cmd/$*
	chmod 755 dist/$*/bootstrap
	cd dist/$* && zip ../$*.zip bootstrap

build-all: build-login build-authorizer build-email

clean:
	rm -rf dist/
EOF
```

- [ ] **Step 5: Commit**

```bash
gh repo edit ivanzao/auto-repair-shop-lambdas --add-collaborator soat-architecture
git add .gitignore go.mod go.sum Makefile
git commit -m "chore: bootstrap Go monorepo with deps and Makefile"
git push -u origin main
```

---

## Task 2: `internal/secrets/manager.go` — Secrets Manager client

**Files:**
- Create: `internal/secrets/manager.go`, `internal/secrets/manager_test.go`

- [ ] **Step 1: Escrever teste (`manager_test.go`)**

```go
package secrets

import (
	"context"
	"errors"
	"testing"
)

type mockClient struct {
	value string
	err   error
}

func (m *mockClient) Get(ctx context.Context, id string) (string, error) {
	if m.err != nil {
		return "", m.err
	}
	return m.value, nil
}

func TestGet_ReturnsValue(t *testing.T) {
	m := &mockClient{value: `{"password":"abc"}`}
	got, err := m.Get(context.Background(), "any")
	if err != nil { t.Fatal(err) }
	if got != `{"password":"abc"}` {
		t.Fatalf("unexpected value: %s", got)
	}
}

func TestGet_PropagatesError(t *testing.T) {
	m := &mockClient{err: errors.New("boom")}
	_, err := m.Get(context.Background(), "any")
	if err == nil { t.Fatal("expected error") }
}
```

- [ ] **Step 2: Rodar — espera fail**

```bash
go test ./internal/secrets/...
```

Expected: erro `cannot find package` (ainda não criamos `manager.go`).

- [ ] **Step 3: Implementar `manager.go`**

```go
package secrets

import (
	"context"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

type Client interface {
	Get(ctx context.Context, secretID string) (string, error)
}

type smClient struct {
	sm *secretsmanager.Client
}

func New(ctx context.Context) (Client, error) {
	cfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		return nil, err
	}
	return &smClient{sm: secretsmanager.NewFromConfig(cfg)}, nil
}

func (c *smClient) Get(ctx context.Context, secretID string) (string, error) {
	out, err := c.sm.GetSecretValue(ctx, &secretsmanager.GetSecretValueInput{
		SecretId: aws.String(secretID),
	})
	if err != nil {
		return "", err
	}
	return aws.ToString(out.SecretString), nil
}
```

- [ ] **Step 4: Rodar testes — espera pass**

```bash
go test ./internal/secrets/...
```

Expected: `PASS`

- [ ] **Step 5: Commit**

```bash
git add internal/secrets/
git commit -m "feat(secrets): add Secrets Manager client with interface and mockable test"
git push
```

---

## Task 3: `internal/observability/` — slog JSON + OTel tracer

**Files:**
- Create: `internal/observability/logger.go`, `internal/observability/tracer.go`

- [ ] **Step 1: Criar `logger.go`**

```go
package observability

import (
	"log/slog"
	"os"
)

func NewLogger(service string) *slog.Logger {
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})).With(
		slog.String("service", service),
	)
}
```

- [ ] **Step 2: Criar `tracer.go`**

```go
package observability

import (
	"context"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.24.0"
)

func InitTracer(ctx context.Context, service, otlpEndpoint string) (func(context.Context) error, error) {
	exp, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithInsecure(),
		otlptracegrpc.WithEndpoint(otlpEndpoint),
	)
	if err != nil {
		return nil, err
	}

	res, err := resource.New(ctx,
		resource.WithAttributes(semconv.ServiceName(service)),
	)
	if err != nil {
		return nil, err
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)
	return tp.Shutdown, nil
}
```

- [ ] **Step 3: Compilar — espera ok**

```bash
go build ./internal/observability/...
```

- [ ] **Step 4: Commit**

```bash
git add internal/observability/
git commit -m "feat(observability): add JSON logger and OTel tracer init"
git push
```

---

## Task 4: `internal/cpf/` — validação CPF

**Files:**
- Create: `internal/cpf/validate.go`, `internal/cpf/validate_test.go`

- [ ] **Step 1: Escrever teste**

```go
package cpf

import "testing"

func TestValidate(t *testing.T) {
	cases := []struct {
		in   string
		want bool
	}{
		{"529.982.247-25", true},   // CPF válido conhecido
		{"52998224725", true},      // sem formatação
		{"111.111.111-11", false},  // todos iguais
		{"123.456.789-00", false},  // dígito errado
		{"abc", false},
		{"", false},
	}
	for _, c := range cases {
		if got := Validate(c.in); got != c.want {
			t.Errorf("Validate(%q) = %v, want %v", c.in, got, c.want)
		}
	}
}
```

- [ ] **Step 2: Rodar — espera fail**

```bash
go test ./internal/cpf/...
```

- [ ] **Step 3: Implementar `validate.go`**

```go
package cpf

import (
	"regexp"
	"strconv"
	"strings"
)

var onlyDigits = regexp.MustCompile(`\D`)

func Validate(cpf string) bool {
	cpf = onlyDigits.ReplaceAllString(cpf, "")
	if len(cpf) != 11 {
		return false
	}
	// todos iguais → inválido
	if strings.Count(cpf, string(cpf[0])) == 11 {
		return false
	}
	return checkDigit(cpf, 9) && checkDigit(cpf, 10)
}

func checkDigit(cpf string, length int) bool {
	sum := 0
	for i := 0; i < length; i++ {
		d, _ := strconv.Atoi(string(cpf[i]))
		sum += d * (length + 1 - i)
	}
	rest := sum * 10 % 11
	if rest == 10 {
		rest = 0
	}
	expected, _ := strconv.Atoi(string(cpf[length]))
	return rest == expected
}
```

- [ ] **Step 4: Rodar testes**

```bash
go test ./internal/cpf/... -v
```

Expected: todos os cases PASS.

- [ ] **Step 5: Commit**

```bash
git add internal/cpf/
git commit -m "feat(cpf): add CPF validation with check digits"
git push
```

---

## Task 5: `internal/password/` — bcrypt compare

**Files:**
- Create: `internal/password/bcrypt.go`, `internal/password/bcrypt_test.go`

- [ ] **Step 1: Escrever teste**

```go
package password

import (
	"testing"

	"golang.org/x/crypto/bcrypt"
)

func TestVerify(t *testing.T) {
	plain := "secret123"
	hash, _ := bcrypt.GenerateFromPassword([]byte(plain), bcrypt.DefaultCost)

	if !Verify(string(hash), plain) {
		t.Error("Verify() should accept correct password")
	}
	if Verify(string(hash), "wrong") {
		t.Error("Verify() should reject wrong password")
	}
}
```

- [ ] **Step 2: Implementar `bcrypt.go`**

```go
package password

import "golang.org/x/crypto/bcrypt"

func Verify(hash, plain string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(plain))
	return err == nil
}
```

- [ ] **Step 3: Testar + commit**

```bash
go test ./internal/password/... -v
git add internal/password/
git commit -m "feat(password): add bcrypt verify"
git push
```

---

## Task 6: `internal/jwt/` — HMAC512 signer + verifier

**Files:**
- Create: `internal/jwt/signer.go`, `internal/jwt/verifier.go`, `internal/jwt/jwt_test.go`

- [ ] **Step 1: Escrever teste**

```go
package jwt

import (
	"testing"
	"time"
)

func TestSignAndVerify_RoundTrip(t *testing.T) {
	secret := "test-secret"
	claims := Claims{
		UserID: "user-123",
		Role:   "ADMIN",
		CPF:    "52998224725",
	}
	token, err := Sign(secret, claims, 1*time.Hour)
	if err != nil { t.Fatal(err) }

	got, err := Verify(secret, token)
	if err != nil { t.Fatal(err) }

	if got.UserID != claims.UserID || got.Role != claims.Role {
		t.Errorf("round-trip mismatch: got %+v, want %+v", got, claims)
	}
}

func TestVerify_RejectsExpired(t *testing.T) {
	secret := "test-secret"
	tok, _ := Sign(secret, Claims{UserID: "x"}, -1*time.Minute)
	if _, err := Verify(secret, tok); err == nil {
		t.Error("Verify should reject expired token")
	}
}

func TestVerify_RejectsWrongSecret(t *testing.T) {
	tok, _ := Sign("a", Claims{UserID: "x"}, time.Hour)
	if _, err := Verify("b", tok); err == nil {
		t.Error("Verify should reject token signed with different secret")
	}
}
```

- [ ] **Step 2: Implementar `signer.go`**

```go
package jwt

import (
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	UserID string `json:"sub"`
	Role   string `json:"role"`
	CPF    string `json:"cpf"`
	jwt.RegisteredClaims
}

func Sign(secret string, c Claims, ttl time.Duration) (string, error) {
	now := time.Now()
	c.RegisteredClaims = jwt.RegisteredClaims{
		Issuer:    "auto-repair-shop",
		Audience:  jwt.ClaimStrings{"auto-repair-shop-api"},
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
		ID:        randomJTI(),
	}
	tok := jwt.NewWithClaims(jwt.SigningMethodHS512, c)
	return tok.SignedString([]byte(secret))
}

func randomJTI() string {
	return time.Now().Format("20060102150405.000000")
}
```

- [ ] **Step 3: Implementar `verifier.go`**

```go
package jwt

import (
	"errors"
	"fmt"

	"github.com/golang-jwt/jwt/v5"
)

func Verify(secret, tokenStr string) (*Claims, error) {
	parsed, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return []byte(secret), nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := parsed.Claims.(*Claims)
	if !ok || !parsed.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}
```

- [ ] **Step 4: Testar + commit**

```bash
go test ./internal/jwt/... -v -race
git add internal/jwt/
git commit -m "feat(jwt): add HMAC512 signer and verifier with claims roundtrip tests"
git push
```

---

## Task 7: `internal/user/` — repository PostgreSQL

**Files:**
- Create: `internal/user/repository.go`, `internal/user/repository_test.go`

- [ ] **Step 1: Definir interface e mock test**

```go
// internal/user/repository_test.go
package user

import (
	"context"
	"errors"
	"testing"
)

type mockRepo struct {
	user *User
	err  error
}

func (m *mockRepo) FindByDocument(ctx context.Context, doc string) (*User, error) {
	return m.user, m.err
}

func TestFindByDocument(t *testing.T) {
	r := &mockRepo{user: &User{ID: "abc", Status: "ACTIVE"}}
	got, err := r.FindByDocument(context.Background(), "12345678900")
	if err != nil { t.Fatal(err) }
	if got.ID != "abc" {
		t.Errorf("unexpected user: %+v", got)
	}
}

func TestFindByDocument_NotFound(t *testing.T) {
	r := &mockRepo{err: errors.New("not found")}
	if _, err := r.FindByDocument(context.Background(), "x"); err == nil {
		t.Error("expected error")
	}
}
```

- [ ] **Step 2: Implementar `repository.go`**

```go
package user

import (
	"context"
	"database/sql"

	_ "github.com/lib/pq"
)

type User struct {
	ID             string
	Document       string
	Role           string
	HashedPassword string
	Status         string
}

type Repository interface {
	FindByDocument(ctx context.Context, doc string) (*User, error)
}

type pgRepo struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) Repository {
	return &pgRepo{db: db}
}

func (r *pgRepo) FindByDocument(ctx context.Context, doc string) (*User, error) {
	row := r.db.QueryRowContext(ctx,
		`SELECT id::text, document, role, hashed_password, status
		 FROM users
		 WHERE document = $1 AND status = 'ACTIVE'`,
		doc,
	)
	var u User
	if err := row.Scan(&u.ID, &u.Document, &u.Role, &u.HashedPassword, &u.Status); err != nil {
		return nil, err
	}
	return &u, nil
}
```

- [ ] **Step 3: Testar + commit**

```bash
go test ./internal/user/... -v
git add internal/user/
git commit -m "feat(user): add User model and PostgreSQL repository"
git push
```

---

## Task 8: `internal/mailersend/` + `internal/template/` + `internal/model/`

**Files:**
- Create: `internal/mailersend/client.go`, `internal/template/quote.go`, `internal/model/event.go`

- [ ] **Step 1: `internal/model/event.go`**

```go
package model

type QuoteEmailRequestedEvent struct {
	EventID       string `json:"event_id"`
	OrderID       string `json:"order_id"`
	CustomerEmail string `json:"customer_email"`
	CustomerName  string `json:"customer_name"`
	TotalAmount   string `json:"total_amount"`
	Items         []Item `json:"items"`
}

type Item struct {
	Description string `json:"description"`
	UnitPrice   string `json:"unit_price"`
	Quantity    int    `json:"quantity"`
}
```

- [ ] **Step 2: `internal/template/quote.go`**

```go
package template

import (
	"bytes"
	"html/template"

	"github.com/ivanzao/auto-repair-shop-lambdas/internal/model"
)

const quoteHTML = `<!DOCTYPE html>
<html><body>
<p>Olá, {{.CustomerName}}.</p>
<p>Segue o orçamento da ordem {{.OrderID}}:</p>
<table border="1">
  <tr><th>Descrição</th><th>Qtd</th><th>Preço unitário</th></tr>
  {{range .Items}}<tr><td>{{.Description}}</td><td>{{.Quantity}}</td><td>{{.UnitPrice}}</td></tr>{{end}}
</table>
<p><strong>Total:</strong> {{.TotalAmount}}</p>
</body></html>`

func RenderQuote(ev model.QuoteEmailRequestedEvent) (string, error) {
	t, err := template.New("quote").Parse(quoteHTML)
	if err != nil { return "", err }
	var buf bytes.Buffer
	if err := t.Execute(&buf, ev); err != nil { return "", err }
	return buf.String(), nil
}
```

- [ ] **Step 3: `internal/mailersend/client.go`**

```go
package mailersend

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

type Client struct {
	token   string
	baseURL string
	http    *http.Client
}

func New(token string) *Client {
	return &Client{
		token:   token,
		baseURL: "https://api.mailersend.com/v1",
		http:    &http.Client{Timeout: 0},
	}
}

type SendRequest struct {
	From    Address `json:"from"`
	To      []Address `json:"to"`
	Subject string  `json:"subject"`
	HTML    string  `json:"html"`
}

type Address struct {
	Email string `json:"email"`
	Name  string `json:"name,omitempty"`
}

func (c *Client) Send(ctx context.Context, req SendRequest, idempotencyKey string) error {
	body, _ := json.Marshal(req)
	httpReq, err := http.NewRequestWithContext(ctx, "POST", c.baseURL+"/email", bytes.NewReader(body))
	if err != nil { return err }
	httpReq.Header.Set("Authorization", "Bearer "+c.token)
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("X-Requested-With", "XMLHttpRequest")
	if idempotencyKey != "" {
		httpReq.Header.Set("Idempotency-Key", idempotencyKey)
	}
	resp, err := c.http.Do(httpReq)
	if err != nil { return err }
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("mailersend: %d — %s", resp.StatusCode, string(b))
	}
	return nil
}
```

- [ ] **Step 4: Build check + commit**

```bash
go build ./internal/...
git add internal/mailersend/ internal/template/ internal/model/
git commit -m "feat: add mailersend client, quote template, and event model"
git push
```

---

## Task 9: `cmd/login/main.go` — handler de login

**Files:**
- Create: `cmd/login/main.go`

- [ ] **Step 1: Implementar handler**

```go
package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"os"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/cpf"
	jwtpkg "github.com/ivanzao/auto-repair-shop-lambdas/internal/jwt"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/observability"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/password"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/secrets"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/user"
	"time"
)

type LoginReq struct {
	CPF      string `json:"cpf"`
	Password string `json:"password"`
}

type LoginResp struct {
	Token string `json:"token"`
}

var (
	logger = observability.NewLogger("auth-login")
	repo   user.Repository
	jwtSec string
)

func init() {
	ctx := context.Background()
	sm, err := secrets.New(ctx)
	if err != nil { panic(err) }

	// DB secret
	dbSecretID := os.Getenv("DB_SECRET_ID") // ex.: auto-repair-shop/db-password-hml
	raw, err := sm.Get(ctx, dbSecretID)
	if err != nil { panic(err) }
	var dbCred struct {
		Username, Password, Host, Database string
		Port                               int
	}
	if err := json.Unmarshal([]byte(raw), &dbCred); err != nil { panic(err) }

	dsn := fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=require",
		dbCred.Host, dbCred.Port, dbCred.Username, dbCred.Password, dbCred.Database)
	db, err := sql.Open("postgres", dsn)
	if err != nil { panic(err) }
	repo = user.NewRepository(db)

	// JWT secret
	jwtSecretID := os.Getenv("JWT_SECRET_ID")
	jwtSec, err = sm.Get(ctx, jwtSecretID)
	if err != nil { panic(err) }
}

func handler(ctx context.Context, req events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	var body LoginReq
	if err := json.Unmarshal([]byte(req.Body), &body); err != nil {
		return errResp(400, "invalid body"), nil
	}
	if !cpf.Validate(body.CPF) {
		return errResp(401, "invalid credentials"), nil
	}
	doc := normalizeCPF(body.CPF)
	u, err := repo.FindByDocument(ctx, doc)
	if err != nil {
		logger.Info("login failed: user not found", "cpf", doc)
		return errResp(401, "invalid credentials"), nil
	}
	if !password.Verify(u.HashedPassword, body.Password) {
		return errResp(401, "invalid credentials"), nil
	}
	tok, err := jwtpkg.Sign(jwtSec, jwtpkg.Claims{UserID: u.ID, Role: u.Role, CPF: doc}, 1*time.Hour)
	if err != nil { return errResp(500, "internal"), nil }
	out, _ := json.Marshal(LoginResp{Token: tok})
	return events.APIGatewayV2HTTPResponse{
		StatusCode: 200,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(out),
	}, nil
}

func normalizeCPF(s string) string {
	out := make([]rune, 0, len(s))
	for _, r := range s {
		if r >= '0' && r <= '9' { out = append(out, r) }
	}
	return string(out)
}

func errResp(code int, msg string) events.APIGatewayV2HTTPResponse {
	body, _ := json.Marshal(map[string]string{"error": msg})
	return events.APIGatewayV2HTTPResponse{
		StatusCode: code,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(body),
	}
}

func main() {
	lambda.Start(handler)
}
```

- [ ] **Step 2: Build local**

```bash
make build-login
```

Expected: `dist/login.zip` criado.

- [ ] **Step 3: Commit**

```bash
git add cmd/login/
git commit -m "feat(login): add Lambda handler validating CPF+password and issuing JWT"
git push
```

---

## Task 10: `cmd/authorizer/main.go` — request authorizer v2.0

**Files:**
- Create: `cmd/authorizer/main.go`

- [ ] **Step 1: Implementar**

```go
package main

import (
	"context"
	"os"
	"strings"

	"github.com/aws/aws-lambda-go/lambda"
	jwtpkg "github.com/ivanzao/auto-repair-shop-lambdas/internal/jwt"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/observability"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/secrets"
)

type Event struct {
	Headers map[string]string `json:"headers"`
}

type Response struct {
	IsAuthorized bool                   `json:"isAuthorized"`
	Context      map[string]interface{} `json:"context"`
}

var (
	logger = observability.NewLogger("auth-authorizer")
	jwtSec string
)

func init() {
	ctx := context.Background()
	sm, err := secrets.New(ctx)
	if err != nil { panic(err) }
	jwtSec, err = sm.Get(ctx, os.Getenv("JWT_SECRET_ID"))
	if err != nil { panic(err) }
}

func handler(ctx context.Context, ev Event) (Response, error) {
	auth := ev.Headers["authorization"]
	if auth == "" { auth = ev.Headers["Authorization"] }
	tok := strings.TrimPrefix(auth, "Bearer ")
	if tok == auth || tok == "" {
		return Response{IsAuthorized: false}, nil
	}
	claims, err := jwtpkg.Verify(jwtSec, tok)
	if err != nil {
		logger.Info("authorizer rejected", "err", err.Error())
		return Response{IsAuthorized: false}, nil
	}
	return Response{
		IsAuthorized: true,
		Context: map[string]interface{}{
			"userId": claims.UserID,
			"role":   claims.Role,
			"cpf":    claims.CPF,
		},
	}, nil
}

func main() {
	lambda.Start(handler)
}
```

- [ ] **Step 2: Build local + commit**

```bash
make build-authorizer
git add cmd/authorizer/
git commit -m "feat(authorizer): add API GW v2.0 request authorizer with JWT verify"
git push
```

---

## Task 11: `cmd/email/main.go` — SQS consumer → MailerSend

**Files:**
- Create: `cmd/email/main.go`

- [ ] **Step 1: Implementar**

```go
package main

import (
	"context"
	"encoding/json"
	"os"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/mailersend"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/model"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/observability"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/secrets"
	"github.com/ivanzao/auto-repair-shop-lambdas/internal/template"
)

var (
	logger = observability.NewLogger("email-handler")
	client *mailersend.Client
)

func init() {
	ctx := context.Background()
	sm, err := secrets.New(ctx)
	if err != nil { panic(err) }
	raw, err := sm.Get(ctx, os.Getenv("MAILERSEND_SECRET_ID"))
	if err != nil { panic(err) }
	var sec struct{ Token string }
	if err := json.Unmarshal([]byte(raw), &sec); err != nil {
		// fallback: secret bruto (sem JSON)
		sec.Token = raw
	}
	client = mailersend.New(sec.Token)
}

type SNSWrapper struct {
	Message string `json:"Message"`
}

func handler(ctx context.Context, sqsEvent events.SQSEvent) (events.SQSEventResponse, error) {
	var failures []events.SQSBatchItemFailure
	for _, rec := range sqsEvent.Records {
		var wrapper SNSWrapper
		if err := json.Unmarshal([]byte(rec.Body), &wrapper); err != nil {
			logger.Error("invalid SNS wrapper", "id", rec.MessageId, "err", err)
			failures = append(failures, events.SQSBatchItemFailure{ItemIdentifier: rec.MessageId})
			continue
		}
		var ev model.QuoteEmailRequestedEvent
		if err := json.Unmarshal([]byte(wrapper.Message), &ev); err != nil {
			logger.Error("invalid event payload", "id", rec.MessageId, "err", err)
			failures = append(failures, events.SQSBatchItemFailure{ItemIdentifier: rec.MessageId})
			continue
		}
		html, err := template.RenderQuote(ev)
		if err != nil {
			failures = append(failures, events.SQSBatchItemFailure{ItemIdentifier: rec.MessageId})
			continue
		}
		err = client.Send(ctx, mailersend.SendRequest{
			From:    mailersend.Address{Email: os.Getenv("MAIL_FROM"), Name: "Auto Repair Shop"},
			To:      []mailersend.Address{{Email: ev.CustomerEmail, Name: ev.CustomerName}},
			Subject: "Orçamento da sua ordem " + ev.OrderID,
			HTML:    html,
		}, ev.EventID)
		if err != nil {
			logger.Error("send failed", "id", rec.MessageId, "err", err)
			failures = append(failures, events.SQSBatchItemFailure{ItemIdentifier: rec.MessageId})
			continue
		}
		logger.Info("email sent", "order_id", ev.OrderID)
	}
	return events.SQSEventResponse{BatchItemFailures: failures}, nil
}

func main() {
	lambda.Start(handler)
}
```

- [ ] **Step 2: Build + commit**

```bash
make build-email
git add cmd/email/
git commit -m "feat(email): add SQS consumer Lambda calling MailerSend with idempotency"
git push
```

---

## Task 12: Terraform `terraform/auth/`

**Files:**
- Create: `terraform/auth/{backend,providers,variables,data,lambda,apigw,secrets,outputs,ssm}.tf`

- [ ] **Step 1: backend, providers, variables**

```bash
cd terraform/auth
```

`backend.tf`:
```hcl
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.60" }
  }
  backend "s3" {
    bucket  = "auto-repair-shop-tfstate-<ACCOUNT_ID>"
    key     = "lambdas/auth/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
```

`providers.tf`:
```hcl
provider "aws" { region = var.aws_region }
```

`variables.tf`:
```hcl
variable "aws_region"       { type = string default = "us-east-1" }
variable "environment"      { type = string }  # hml ou prod
variable "lambda_zip_path"  { type = string }  # caminho do zip do login
variable "authorizer_zip"   { type = string }  # caminho do zip do authorizer
variable "jwt_hmac_secret"  { type = string sensitive = true }
```

`data.tf`:
```hcl
data "aws_caller_identity" "current" {}

data "terraform_remote_state" "infra_k8s" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/network-k8s/terraform.tfstate"
    region = var.aws_region
  }
}

data "terraform_remote_state" "infra_db" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/database/terraform.tfstate"
    region = var.aws_region
  }
}

data "aws_iam_role" "lab_role" { name = "LabRole" }

locals {
  private_subnet_ids = data.terraform_remote_state.infra_k8s.outputs.private_subnet_ids
  lambda_sg_id       = data.terraform_remote_state.infra_k8s.outputs.lambda_sg_id
  db_secret_arn      = var.environment == "hml" ?
    data.terraform_remote_state.infra_db.outputs.db_hml_secret_arn :
    data.terraform_remote_state.infra_db.outputs.db_prod_secret_arn
}
```

- [ ] **Step 2: `secrets.tf` — JWT HMAC**

```hcl
resource "aws_secretsmanager_secret" "jwt" {
  name                    = "auto-repair-shop/jwt-hmac-${var.environment}"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = var.jwt_hmac_secret
}
```

- [ ] **Step 3: `lambda.tf`**

```hcl
resource "aws_lambda_function" "login" {
  function_name    = "auto-repair-shop-login-${var.environment}"
  filename         = var.lambda_zip_path
  source_code_hash = filebase64sha256(var.lambda_zip_path)
  handler          = "bootstrap"
  runtime          = "provided.al2023"
  architectures    = ["arm64"]
  role             = data.aws_iam_role.lab_role.arn
  timeout          = 15

  vpc_config {
    subnet_ids         = local.private_subnet_ids
    security_group_ids = [local.lambda_sg_id]
  }

  environment {
    variables = {
      DB_SECRET_ID  = local.db_secret_arn
      JWT_SECRET_ID = aws_secretsmanager_secret.jwt.arn
    }
  }
}

resource "aws_lambda_function" "authorizer" {
  function_name    = "auto-repair-shop-authorizer-${var.environment}"
  filename         = var.authorizer_zip
  source_code_hash = filebase64sha256(var.authorizer_zip)
  handler          = "bootstrap"
  runtime          = "provided.al2023"
  architectures    = ["arm64"]
  role             = data.aws_iam_role.lab_role.arn
  timeout          = 5

  vpc_config {
    subnet_ids         = local.private_subnet_ids
    security_group_ids = [local.lambda_sg_id]
  }

  environment {
    variables = {
      JWT_SECRET_ID = aws_secretsmanager_secret.jwt.arn
    }
  }
}
```

- [ ] **Step 4: `apigw.tf`**

```hcl
# NLB do app (criado pelo AWS LB Controller a partir do Service do app)
data "aws_lb" "app" {
  tags = { "kubernetes.io/service-name" = "auto-repair-shop-${var.environment}/auto-repair-shop-service" }
}

resource "aws_apigatewayv2_api" "this" {
  name          = "auto-repair-shop-${var.environment}"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_stage" "this" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_apigatewayv2_integration" "login" {
  api_id                 = aws_apigatewayv2_api.this.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.login.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "login" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "POST /auth/login"
  target    = "integrations/${aws_apigatewayv2_integration.login.id}"
}

resource "aws_lambda_permission" "apigw_login" {
  statement_id  = "AllowAPIGatewayInvokeLogin"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.login.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.this.execution_arn}/*/*"
}

# Authorizer
resource "aws_apigatewayv2_authorizer" "this" {
  api_id                            = aws_apigatewayv2_api.this.id
  authorizer_type                   = "REQUEST"
  identity_sources                  = ["$request.header.Authorization"]
  authorizer_uri                    = aws_lambda_function.authorizer.invoke_arn
  authorizer_payload_format_version = "2.0"
  enable_simple_responses           = true
  authorizer_result_ttl_in_seconds  = 300
  name                              = "jwt-authorizer-${var.environment}"
}

resource "aws_lambda_permission" "apigw_authorizer" {
  statement_id  = "AllowAPIGatewayInvokeAuthorizer"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.this.execution_arn}/authorizers/${aws_apigatewayv2_authorizer.this.id}"
}

# VPC Link → NLB privado do app no EKS
resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "auto-repair-shop-${var.environment}"
  security_group_ids = [local.lambda_sg_id]
  subnet_ids         = local.private_subnet_ids
}

resource "aws_apigatewayv2_integration" "app" {
  api_id             = aws_apigatewayv2_api.this.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = data.aws_lb.app.arn  # ALB/NLB listener ARN
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.this.id
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:header.X-User-Id"   = "$context.authorizer.userId"
    "overwrite:header.X-User-Role" = "$context.authorizer.role"
  }
}

resource "aws_apigatewayv2_route" "app_protected" {
  api_id             = aws_apigatewayv2_api.this.id
  route_key          = "ANY /v1/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.app.id}"
  authorizer_id      = aws_apigatewayv2_authorizer.this.id
  authorization_type = "CUSTOM"
}
```

- [ ] **Step 5: `outputs.tf` + `ssm.tf`**

```hcl
# outputs.tf
output "apigw_endpoint" { value = aws_apigatewayv2_api.this.api_endpoint }
output "login_arn"      { value = aws_lambda_function.login.arn }
output "authorizer_arn" { value = aws_lambda_function.authorizer.arn }

# ssm.tf
resource "aws_ssm_parameter" "apigw_endpoint" {
  name      = "/auto-repair-shop/${var.environment}/apigw/endpoint"
  type      = "String"
  value     = aws_apigatewayv2_api.this.api_endpoint
  overwrite = true
}
```

- [ ] **Step 6: Substituir `<ACCOUNT_ID>` no backend + init**

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i.bak "s/<ACCOUNT_ID>/$ACCOUNT_ID/" backend.tf && rm backend.tf.bak

cd ../..  # voltar pra raiz do repo
git add terraform/auth/
git commit -m "feat(tf-auth): add Lambda + API Gateway + Authorizer + VPC Link Terraform"
git push
```

---

## Task 13: Terraform `terraform/email/`

**Files:**
- Create: `terraform/email/{backend,providers,variables,data,sns,sqs,subscription,lambda,eventsource,secrets,outputs,ssm}.tf`

- [ ] **Step 1: backend + providers + variables**

```bash
cd terraform/email
```

`backend.tf` similar ao auth, mas key `lambdas/email/terraform.tfstate`.

`variables.tf`:
```hcl
variable "aws_region"          { type = string default = "us-east-1" }
variable "environment"         { type = string }
variable "email_zip_path"      { type = string }
variable "mailersend_token"    { type = string sensitive = true }
variable "mail_from"           { type = string default = "noreply@auto-repair-shop.com" }
```

`data.tf` (similar ao auth, mas só lê network-k8s):
```hcl
data "aws_caller_identity" "current" {}

data "terraform_remote_state" "infra_k8s" {
  backend = "s3"
  config = {
    bucket = "auto-repair-shop-tfstate-${data.aws_caller_identity.current.account_id}"
    key    = "infra/network-k8s/terraform.tfstate"
    region = var.aws_region
  }
}

data "aws_iam_role" "lab_role" { name = "LabRole" }

locals {
  private_subnet_ids = data.terraform_remote_state.infra_k8s.outputs.private_subnet_ids
  lambda_sg_id       = data.terraform_remote_state.infra_k8s.outputs.lambda_sg_id
}
```

- [ ] **Step 2: `sns.tf` + `sqs.tf` + `subscription.tf`**

```hcl
# sns.tf
resource "aws_sns_topic" "events" {
  name = "auto-repair-shop-events-${var.environment}"
}

# sqs.tf
resource "aws_sqs_queue" "email_dlq" {
  name                       = "auto-repair-shop-email-dlq-${var.environment}"
  message_retention_seconds  = 1209600  # 14 dias
}

resource "aws_sqs_queue" "email" {
  name                       = "auto-repair-shop-email-${var.environment}"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 345600   # 4 dias
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.email_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue_policy" "email" {
  queue_url = aws_sqs_queue.email.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.email.arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.events.arn } }
    }]
  })
}

# subscription.tf
resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.events.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.email.arn
  filter_policy = jsonencode({
    event_type = ["QuoteEmailRequested"]
  })
  filter_policy_scope = "MessageAttributes"
}
```

- [ ] **Step 3: `secrets.tf` + `lambda.tf` + `eventsource.tf`**

```hcl
# secrets.tf
resource "aws_secretsmanager_secret" "mailersend" {
  name                    = "auto-repair-shop/mailersend-token"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mailersend" {
  secret_id     = aws_secretsmanager_secret.mailersend.id
  secret_string = jsonencode({ Token = var.mailersend_token })
  lifecycle {
    ignore_changes = [secret_string]  # valor é externo (não geramos)
  }
}

# lambda.tf
resource "aws_lambda_function" "email" {
  function_name    = "auto-repair-shop-email-${var.environment}"
  filename         = var.email_zip_path
  source_code_hash = filebase64sha256(var.email_zip_path)
  handler          = "bootstrap"
  runtime          = "provided.al2023"
  architectures    = ["arm64"]
  role             = data.aws_iam_role.lab_role.arn
  timeout          = 30
  reserved_concurrent_executions = 10

  vpc_config {
    subnet_ids         = local.private_subnet_ids
    security_group_ids = [local.lambda_sg_id]
  }

  environment {
    variables = {
      MAILERSEND_SECRET_ID = aws_secretsmanager_secret.mailersend.arn
      MAIL_FROM            = var.mail_from
    }
  }
}

# eventsource.tf
resource "aws_lambda_event_source_mapping" "email" {
  event_source_arn = aws_sqs_queue.email.arn
  function_name    = aws_lambda_function.email.arn
  batch_size       = 10
  function_response_types = ["ReportBatchItemFailures"]
}
```

- [ ] **Step 4: `outputs.tf` + `ssm.tf`**

```hcl
# outputs.tf
output "sns_topic_arn"   { value = aws_sns_topic.events.arn }
output "sqs_queue_arn"   { value = aws_sqs_queue.email.arn }
output "dlq_arn"         { value = aws_sqs_queue.email_dlq.arn }
output "email_lambda_arn"{ value = aws_lambda_function.email.arn }

# ssm.tf
resource "aws_ssm_parameter" "sns_topic_arn" {
  name      = "/auto-repair-shop/${var.environment}/sns/events-topic-arn"
  type      = "String"
  value     = aws_sns_topic.events.arn
  overwrite = true
}

resource "aws_ssm_parameter" "sqs_queue_arn" {
  name      = "/auto-repair-shop/${var.environment}/sqs/email-queue-arn"
  type      = "String"
  value     = aws_sqs_queue.email.arn
  overwrite = true
}
```

- [ ] **Step 5: Substituir `<ACCOUNT_ID>` + commit**

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i.bak "s/<ACCOUNT_ID>/$ACCOUNT_ID/" backend.tf && rm backend.tf.bak
cd ../..

git add terraform/email/
git commit -m "feat(tf-email): add SNS + SQS + Lambda email + DLQ Terraform"
git push
```

---

## Task 14: `.github/workflows/pr-check.yaml`

**Files:**
- Create: `.github/workflows/pr-check.yaml`

- [ ] **Step 1: Criar workflow**

```yaml
name: PR Check
on:
  pull_request:
    branches: [main, develop]

jobs:
  go:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: "1.23" }
      - run: go vet ./...
      - run: go test ./... -race -cover
      - uses: golangci/golangci-lint-action@v6
        with: { version: latest }

  tf-auth:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
      - working-directory: terraform/auth
        run: |
          terraform fmt -check
          terraform init
          terraform validate

  tf-email:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
      - working-directory: terraform/email
        run: |
          terraform fmt -check
          terraform init
          terraform validate
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/pr-check.yaml
git commit -m "ci: add PR check (go tests + lint + tf validate per subproject)"
git push
```

---

## Task 15: `.github/workflows/deploy.yaml` com path-filter

**Files:**
- Create: `.github/workflows/deploy.yaml`

- [ ] **Step 1: Criar workflow**

```yaml
name: Deploy

on:
  push:
    branches: [main, develop]

jobs:
  detect:
    runs-on: ubuntu-latest
    outputs:
      auth:  ${{ steps.f.outputs.auth }}
      email: ${{ steps.f.outputs.email }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: f
        with:
          filters: |
            auth:
              - 'cmd/login/**'
              - 'cmd/authorizer/**'
              - 'internal/jwt/**'
              - 'internal/cpf/**'
              - 'internal/password/**'
              - 'internal/user/**'
              - 'internal/secrets/**'
              - 'internal/observability/**'
              - 'terraform/auth/**'
            email:
              - 'cmd/email/**'
              - 'internal/mailersend/**'
              - 'internal/template/**'
              - 'internal/model/**'
              - 'internal/secrets/**'
              - 'internal/observability/**'
              - 'terraform/email/**'

  auth:
    needs: detect
    if: needs.detect.outputs.auth == 'true'
    runs-on: ubuntu-latest
    env:
      ENV: ${{ github.ref_name == 'main' && 'prod' || 'hml' }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: "1.23" }
      - run: make build-login build-authorizer
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
      - working-directory: terraform/auth
        env:
          TF_VAR_environment:     ${{ env.ENV }}
          TF_VAR_lambda_zip_path: ../../dist/login.zip
          TF_VAR_authorizer_zip:  ../../dist/authorizer.zip
          TF_VAR_jwt_hmac_secret: ${{ env.ENV == 'prod' && secrets.JWT_HMAC_PROD || secrets.JWT_HMAC_HML }}
        run: |
          terraform init
          terraform apply -auto-approve
      - name: Smoke test login
        run: |
          ENDPOINT=$(aws ssm get-parameter --name /auto-repair-shop/${{ env.ENV }}/apigw/endpoint --query Parameter.Value --output text)
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ENDPOINT/auth/login" -H 'Content-Type: application/json' -d '{"cpf":"00000000000","password":"x"}')
          [ "$STATUS" = "401" ] || (echo "Expected 401, got $STATUS" && exit 1)

  email:
    needs: detect
    if: needs.detect.outputs.email == 'true'
    runs-on: ubuntu-latest
    env:
      ENV: ${{ github.ref_name == 'main' && 'prod' || 'hml' }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: "1.23" }
      - run: make build-email
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws-region: us-east-1
      - uses: hashicorp/setup-terraform@v3
      - working-directory: terraform/email
        env:
          TF_VAR_environment:      ${{ env.ENV }}
          TF_VAR_email_zip_path:   ../../dist/email.zip
          TF_VAR_mailersend_token: ${{ secrets.MAILERSEND_TOKEN }}
        run: |
          terraform init
          terraform apply -auto-approve
```

- [ ] **Step 2: Configurar GitHub Secrets**

```bash
gh secret set AWS_ACCESS_KEY_ID     --body "$AWS_ACCESS_KEY_ID"     --repo ivanzao/auto-repair-shop-lambdas
gh secret set AWS_SECRET_ACCESS_KEY --body "$AWS_SECRET_ACCESS_KEY" --repo ivanzao/auto-repair-shop-lambdas
gh secret set AWS_SESSION_TOKEN     --body "$AWS_SESSION_TOKEN"     --repo ivanzao/auto-repair-shop-lambdas
gh secret set JWT_HMAC_HML  --body "$(openssl rand -base64 64 | tr -d '=+/' | cut -c1-50)" --repo ivanzao/auto-repair-shop-lambdas
gh secret set JWT_HMAC_PROD --body "$(openssl rand -base64 64 | tr -d '=+/' | cut -c1-50)" --repo ivanzao/auto-repair-shop-lambdas
gh secret set MAILERSEND_TOKEN --body "<token-real-do-mailersend>" --repo ivanzao/auto-repair-shop-lambdas
```

- [ ] **Step 3: Commit + branch protection**

```bash
git add .github/workflows/deploy.yaml
git commit -m "ci: add deploy with path-filter for auth/email subprojects"
git push

gh api -X PUT \
  repos/ivanzao/auto-repair-shop-lambdas/branches/main/protection \
  -F required_status_checks[strict]=true \
  -F required_status_checks[contexts][]=go \
  -F enforce_admins=true \
  -F required_pull_request_reviews[required_approving_review_count]=0 \
  -f restrictions=null

# Criar e proteger develop também
git checkout -b develop
git push -u origin develop
gh api -X PUT repos/ivanzao/auto-repair-shop-lambdas/branches/develop/protection \
  -F required_status_checks[strict]=true -F required_status_checks[contexts][]=go \
  -F enforce_admins=true \
  -F required_pull_request_reviews[required_approving_review_count]=0 \
  -f restrictions=null
git checkout main
```

---

## Task 16: README.md

**Files:**
- Create: `README.md`

- [ ] **Step 1: Criar README**

````markdown
# auto-repair-shop-lambdas

Go monorepo with 3 AWS Lambdas:

- **login** — `POST /auth/login` — CPF + password → JWT
- **authorizer** — API Gateway request authorizer (validates JWT, injects `X-User-Id`/`X-User-Role`)
- **email** — SQS consumer that calls MailerSend

Shared `internal/` packages (jwt, secrets, observability, etc) to avoid duplication.

## Build

```bash
make build-all   # produz dist/login.zip, authorizer.zip, email.zip
make test
make lint
```

Build uses Linux/ARM64 (`provided.al2023` custom runtime).

## Terraform sub-projects

- `terraform/auth/` — login + authorizer + API Gateway HTTP API + VPC Link + JWT secrets
- `terraform/email/` — email Lambda + SNS topic + SQS queue + DLQ + MailerSend secret

State files: `lambdas/auth/...` and `lambdas/email/...` (separate, in the shared S3 bucket).

## Deploy

Push to `develop` → deploys to `hml`. Push to `main` → deploys to `prod`. Path-filter ensures only changed sub-projects rebuild/deploy.

## Architecture

See [docs/architecture/](https://github.com/ivanzao/auto-repair-shop/tree/main/docs/architecture/) in the app repo.
````

- [ ] **Step 2: Commit final**

```bash
git add README.md
git commit -m "docs: add README"
git push
```

---

## Critérios de conclusão deste plano

- [ ] `go test ./... -race -cover` passa em local
- [ ] `make build-all` produz 3 zips em `dist/`
- [ ] `terraform apply` em `terraform/auth/` cria login + authorizer Lambdas + API Gateway + authorizer config
- [ ] `terraform apply` em `terraform/email/` cria 2 SNS topics, 2 SQS queues, 2 DLQs, 2 Lambdas email
- [ ] `POST /auth/login { cpf, password }` num user real retorna 200 com JWT
- [ ] `POST /auth/login` com CPF inválido retorna 401
- [ ] Authorizer cache funciona (segunda chamada com mesmo token não invoca authorizer)
- [ ] Publicar mensagem teste no SNS topic dispara Lambda email → email recebido
- [ ] Mensagem inválida vai pra DLQ após 5 retries
- [ ] Pipelines `develop` e `main` rodam e fazem deploy com path-filter funcionando
- [ ] SSM params `/auto-repair-shop/{env}/apigw/endpoint` e `/auto-repair-shop/{env}/sns/events-topic-arn` populados
