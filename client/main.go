package main

import (
	"context"
	"fmt"
	"strings"
	"time"

	kie "kie-grpc-client/kie"

	"google.golang.org/grpc"
)

const (
	address = "localhost:50051"
)

// Main executes the main thread
func Main() {

	conn, _ := grpc.Dial(address, grpc.WithInsecure())
	defer conn.Close()

	client := kie.NewDinnerClient(conn)

	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()

	input := kie.DinnerInput{
		GuestsWithChildren: true,
		Season:             "Fall",
		NumberOfGuests:     4,
		Temp:               25,
		RainProbability:    30}

	result, _ := client.Process(ctx, &input)

	fmt.Println("        Dish:", result.Dish)
	fmt.Println("      Drinks:", strings.Join(result.Drinks, ", "))
	fmt.Println("Where to eat:", result.WhereToEat)
}
